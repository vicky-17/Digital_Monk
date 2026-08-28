package com.digitalmonk.app.core.utils

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.digitalmonk.app.data.local.db.AppDatabase
import com.digitalmonk.app.data.local.db.entity.AppUsageEntity
import com.digitalmonk.app.data.models.AppUsageInfo
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.util.Calendar

class UsageStatsHelper(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager
    private val appUsageDao = AppDatabase.getDatabase(context).appUsageDao()

    fun getUsageStatsForDay(date: Calendar): List<AppUsageInfo> {
        val localDate = LocalDate.of(
            date.get(Calendar.YEAR),
            date.get(Calendar.MONTH) + 1,
            date.get(Calendar.DAY_OF_MONTH)
        )
        val dateKey = TimeUtils.dayKey(localDate)
        
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
        
        val systemStats = getUsageStatsInRange(startTime, endTime)
        val dbStats = runBlocking { appUsageDao.getForDate(dateKey) }.mapNotNull { it.toAppUsageInfo() }
        
        return mergeUsageStats(systemStats, dbStats)
    }

    fun getTodayUsageStats(): List<AppUsageInfo> {
        val dateKey = TimeUtils.todayKey()
        
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        // Ensure we start exactly at the beginning of today
        val startTime = calendar.timeInMillis + 1
        
        val systemStats = getUsageStatsInRange(startTime, endTime)
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
        // Use queryAndAggregateUsageStats for "today so far" as it's more robust than INTERVAL_DAILY
        // which can sometimes return the previous day's bucket if the current one isn't ready.
        val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        val aggregatedMap = mutableMapOf<String, AppUsageInfo>()

        // Get default launcher to categorize it
        val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val launcherPackage = resolveInfo?.activityInfo?.packageName

        statsMap.forEach { (pkg, stat) ->
            val time = stat.totalTimeInForeground
            
            if (time <= 0) return@forEach
            // Only skip very specific system internals that Regain also skips
            if (pkg == "com.android.systemui" || pkg == "android") return@forEach

            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                // Filter out non-launchable apps unless it's our own or a known important one
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

                aggregatedMap[pkg] = AppUsageInfo(
                    packageName = pkg,
                    appName = packageManager.getApplicationLabel(appInfo).toString(),
                    icon = packageManager.getApplicationIcon(appInfo),
                    usageTimeMs = time,
                    // Note: UsageStats.appLaunchCount is only available on API 28+
                    launchCount = 0,
                    category = category
                )
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
