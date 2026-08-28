package com.digitalmonk.app.core.utils

import android.app.usage.UsageEvents
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
        
        val dbStats = runBlocking { appUsageDao.getForDate(dateKey) }
        if (dbStats.isNotEmpty()) {
            return dbStats.mapNotNull { it.toAppUsageInfo() }.sortedByDescending { it.usageTimeMs }
        }

        val calendar = date.clone() as Calendar
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endTime = calendar.timeInMillis
        
        return getUsageStatsInRange(startTime, endTime)
    }

    fun getTodayUsageStats(): List<AppUsageInfo> {
        val dateKey = TimeUtils.todayKey()
        val dbStats = runBlocking { appUsageDao.getForDate(dateKey) }
        if (dbStats.isNotEmpty()) {
            return dbStats.mapNotNull { it.toAppUsageInfo() }.sortedByDescending { it.usageTimeMs }
        }

        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        
        return getUsageStatsInRange(startTime, endTime)
    }

    private fun getUsageStatsInRange(startTime: Long, endTime: Long): List<AppUsageInfo> {
        val events = usageStatsManager.queryEvents(startTime, endTime)
        
        val moveToForegroundMap = mutableMapOf<String, Long>()
        val usageMap = mutableMapOf<String, Long>()
        val launchMap = mutableMapOf<String, Int>()

        // Get default launcher to exclude it from usage
        val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val launcherPackage = resolveInfo?.activityInfo?.packageName

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            
            // Skip launcher and system UI components
            if (pkg == launcherPackage || pkg == "com.android.systemui" || pkg == "android" || pkg == context.packageName) continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED, 1 -> {
                    moveToForegroundMap[pkg] = event.timeStamp
                    launchMap[pkg] = (launchMap[pkg] ?: 0) + 1
                }
                UsageEvents.Event.ACTIVITY_PAUSED, 2,
                UsageEvents.Event.ACTIVITY_STOPPED, 23 -> {
                    val start = moveToForegroundMap[pkg]
                    if (start != null) {
                        val duration = event.timeStamp - start
                        if (duration > 0) { 
                            usageMap[pkg] = (usageMap[pkg] ?: 0L) + duration
                        }
                        moveToForegroundMap.remove(pkg)
                    }
                }
            }
        }

        // Handle apps currently in foreground (unclosed sessions)
        val now = System.currentTimeMillis()
        val actualEndTime = if (endTime > now) now else endTime
        moveToForegroundMap.forEach { (pkg, start) ->
            if (actualEndTime > start) {
                usageMap[pkg] = (usageMap[pkg] ?: 0L) + (actualEndTime - start)
            }
        }

        val finalStats = usageMap.mapNotNull { (pkg, time) ->
            if (time <= 0 && (launchMap[pkg] ?: 0) <= 0) return@mapNotNull null
            
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                if (packageManager.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null
                
                val category = when (appInfo.category) {
                    android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Game"
                    android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                    android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                    android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "Video"
                    android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "Audio"
                    else -> "App"
                }

                AppUsageInfo(
                    packageName = pkg,
                    appName = packageManager.getApplicationLabel(appInfo).toString(),
                    icon = packageManager.getApplicationIcon(appInfo),
                    usageTimeMs = time,
                    launchCount = launchMap[pkg] ?: 0,
                    category = category
                )
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }.sortedByDescending { it.usageTimeMs }

        // Fallback to queryUsageStats if queryEvents returned nothing
        if (finalStats.isEmpty()) {
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            val aggregatedMap = mutableMapOf<String, Long>()
            stats.forEach { 
                if (it.totalTimeInForeground > 0) {
                    aggregatedMap[it.packageName] = (aggregatedMap[it.packageName] ?: 0L) + it.totalTimeInForeground
                }
            }
            
            return aggregatedMap.mapNotNull { (pkg, time) ->
                if (pkg == launcherPackage || pkg == "com.android.systemui" || pkg == "android" || pkg == context.packageName) return@mapNotNull null
                
                try {
                    val appInfo = packageManager.getApplicationInfo(pkg, 0)
                    if (packageManager.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null
                    
                    AppUsageInfo(
                        packageName = pkg,
                        appName = packageManager.getApplicationLabel(appInfo).toString(),
                        icon = packageManager.getApplicationIcon(appInfo),
                        usageTimeMs = time,
                        launchCount = 0,
                        category = "App"
                    )
                } catch (_: Exception) {
                    null
                }
            }.sortedByDescending { it.usageTimeMs }
        }

        return finalStats
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
