package com.digitalmonk.app.core.utils

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.digitalmonk.app.data.local.db.AppDatabase
import com.digitalmonk.app.data.local.db.entity.AppUsageEntity
import com.digitalmonk.app.data.models.AppUsageInfo
import kotlinx.coroutines.runBlocking
import java.util.Calendar

class UsageStatsHelper(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager
    private val appUsageDao = AppDatabase.getDatabase(context).appUsageDao()

    fun getUsageStatsForDay(date: Calendar): List<AppUsageInfo> {
        val calendar = date.clone() as Calendar
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        // Ensure we are inside the target day (UsageStatsManager buckets are strict)
        val startTime = calendar.timeInMillis + 1
        
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        // Stay within the target day
        val endTime = calendar.timeInMillis - 1
        
        // For historical days, trust the System (UsageStatsManager) 100%
        // because that's what Digital Wellbeing does. We don't merge with DB
        // to avoid inflation from potentially noisy Accessibility data.
        return getUsageStatsInRange(startTime, endTime)
    }

    fun getTodayUsageStats(): List<AppUsageInfo> {
        val dateKey = TimeUtils.todayKey()
        
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // Use 1 second after midnight as start time to strictly avoid yesterday's buckets
        val startTime = calendar.timeInMillis + 1000
        
        val systemStats = if (endTime > startTime) {
            getUsageStatsInRange(startTime, endTime)
        } else {
            emptyList()
        }
        val dbStats = runBlocking { appUsageDao.getForDate(dateKey) }.mapNotNull { it.toAppUsageInfo() }
        
        return mergeUsageStats(systemStats, dbStats)
    }

    private fun mergeUsageStats(system: List<AppUsageInfo>, db: List<AppUsageInfo>): List<AppUsageInfo> {
        val mergedMap = mutableMapOf<String, AppUsageInfo>()
        
        // Add all from system
        system.forEach { mergedMap[it.packageName] = it }
        
        // Merge from DB (Accessibility)
        db.forEach { dbInfo ->
            val existing = mergedMap[dbInfo.packageName]
            if (existing != null) {
                // Combine: Take the maximum to ensure we don't under-report
                // Sometimes Accessibility is more accurate for the last session,
                // while UsageStats is better for overall day.
                mergedMap[dbInfo.packageName] = existing.copy(
                    usageTimeMs = maxOf(existing.usageTimeMs, dbInfo.usageTimeMs),
                    launchCount = maxOf(existing.launchCount, dbInfo.launchCount)
                )
            } else {
                mergedMap[dbInfo.packageName] = dbInfo
            }
        }
        
        return mergedMap.values.sortedByDescending { it.usageTimeMs }
    }

    private fun getUsageStatsInRange(startTime: Long, endTime: Long): List<AppUsageInfo> {
        // Use queryUsageStats with INTERVAL_DAILY and manual filtering.
        // queryAndAggregateUsageStats is too aggressive and often returns yesterday's bucket
        // during the first hour of a new day.
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val aggregatedMap = mutableMapOf<String, AppUsageInfo>()

        // Approximate midnight for the day in the range (usually 1s before startTime)
        val targetMidnight = startTime - 1000

        // Get default launcher to categorize it
        val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val launcherPackage = resolveInfo?.activityInfo?.packageName

        stats?.forEach { stat ->
            // CRITICAL: Filter out any buckets that started before today's midnight.
            // This prevents "leaking" yesterday's 24h data into today's view.
            if (stat.firstTimeStamp < targetMidnight - 5000) return@forEach
            
            val pkg = stat.packageName
            val time = stat.totalTimeInForeground
            
            if (time <= 0) return@forEach
            if (pkg == "com.android.systemui" || pkg == "android") return@forEach

            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                if (packageManager.getLaunchIntentForPackage(pkg) == null && pkg != context.packageName) return@forEach

                val category = when {
                    pkg == launcherPackage -> "Launcher"
                    appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Game"
                    appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                    appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                    appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "Video"
                    appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "Audio"
                    else -> "App"
                }

                // If multiple buckets are returned for the same app, sum them up
                val existing = aggregatedMap[pkg]
                if (existing != null) {
                    aggregatedMap[pkg] = existing.copy(usageTimeMs = existing.usageTimeMs + time)
                } else {
                    aggregatedMap[pkg] = AppUsageInfo(
                        packageName = pkg,
                        appName = packageManager.getApplicationLabel(appInfo).toString(),
                        icon = packageManager.getApplicationIcon(appInfo),
                        usageTimeMs = time,
                        launchCount = 0,
                        category = category
                    )
                }
            } catch (_: Exception) {}
        }

        return aggregatedMap.values.sortedByDescending { it.usageTimeMs }
    }
    
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun AppUsageEntity.toAppUsageInfo(): AppUsageInfo? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            if (packageManager.getLaunchIntentForPackage(packageName) == null) return null

            val category = when (appInfo.category) {
                android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Game"
                android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "Video"
                android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "Audio"
                else -> "App"
            }

            AppUsageInfo(
                packageName = packageName,
                appName = packageManager.getApplicationLabel(appInfo).toString(),
                icon = packageManager.getApplicationIcon(appInfo),
                usageTimeMs = totalTime,
                launchCount = launchCount,
                category = category
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
