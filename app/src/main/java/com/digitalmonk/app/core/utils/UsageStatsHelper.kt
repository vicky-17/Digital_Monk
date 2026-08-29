package com.digitalmonk.app.core.utils

import android.content.Context
import android.content.pm.PackageManager
import com.digitalmonk.app.data.models.AppUsageInfo
import java.util.Calendar

class UsageStatsHelper(private val context: Context) {

    private val packageManager = context.packageManager

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
        return getUsageStatsForDay(Calendar.getInstance())
    }


    private fun getUsageStatsInRange(startTime: Long, endTime: Long): List<AppUsageInfo> {
        val result = UsageReconstructor.calculate(context, startTime, endTime)

        return result.usages.mapNotNull { usage ->
            val pkg = usage.packageName
            if (usage.foregroundMs <= 0) return@mapNotNull null
            // Strictly exclude system UI and android internals
            if (pkg == "com.android.systemui" || pkg == "android") return@mapNotNull null

            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                // Filter out non-launchable apps unless it's our own
                if (packageManager.getLaunchIntentForPackage(pkg) == null && pkg != context.packageName) return@mapNotNull null

                val category = when {
                    usage.isLauncher -> "Launcher"
                    appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Game"
                    appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                    appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                    appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "Video"
                    appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "Audio"
                    else -> "App"
                }

                AppUsageInfo(
                    packageName = pkg,
                    appName = packageManager.getApplicationLabel(appInfo).toString(),
                    icon = packageManager.getApplicationIcon(appInfo),
                    usageTimeMs = usage.foregroundMs,
                    launchCount = 0,
                    category = category
                )
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.usageTimeMs }
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

}
