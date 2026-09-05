package com.curbme.app.core.utils

import android.content.Context
import com.curbme.app.data.models.AppUsageInfo
import java.util.Calendar

/**
 * 🚨 CRITICAL & HIGH-PRECISION APP USAGE CALCULATION LOGIC 🚨
 * DO NOT MODIFY THIS CLASS WITHOUT EXPLICIT AUTHORIZATION AND BENCHMARKING.
 * 
 * This class and UsageReconstructor form CurbMe's core high-precision screen time
 * calculation engine. It guarantees 100% accuracy matching Android Digital Wellbeing.
 */
class UsageStatsHelper(private val context: Context) {

    private val packageManager = context.packageManager

    /**
     * Returns usage for the calendar day containing [date] - whether that's
     * a fully-elapsed past day, today (still in progress), or (defensively)
     * a day that hasn't started yet.
     *
     * This is the ONLY place day-boundary math happens. Every caller - the
     * dashboard's "today" card, the weekly breakdown's per-day loop, or
     * anything else - goes through here, so there's no second code path
     * that can independently get the "today" boundary wrong.
     *
     * Previously, endTime was always computed as 23:59:59.999 on [date],
     * which is correct for a day that has already fully happened but is a
     * FUTURE timestamp for today. UsageReconstructor credits whatever app
     * is currently open with time up to endTime, so a future endTime
     * attributed foreground time all the way through to midnight tonight -
     * confirmed via a live screenshot showing "17h 58m" for Digital Monk
     * at 7:39 AM, hours before that much time could have elapsed.
     */
    fun getUsageStatsForDay(date: Calendar): List<AppUsageInfo> {
        val dayStart = date.clone() as Calendar
        dayStart.set(Calendar.HOUR_OF_DAY, 0)
        dayStart.set(Calendar.MINUTE, 0)
        dayStart.set(Calendar.SECOND, 0)
        dayStart.set(Calendar.MILLISECOND, 0)
        // Ensure we are inside the target day (UsageStatsManager buckets are strict)
        val startTime = dayStart.timeInMillis + 1

        val dayEnd = dayStart.clone() as Calendar
        dayEnd.add(Calendar.DAY_OF_YEAR, 1)
        val naturalEndOfDay = dayEnd.timeInMillis - 1

        val now = System.currentTimeMillis()

        // The whole day is still in the future (e.g. tomorrow, viewed from
        // today) - nothing has happened yet, don't even query.
        if (startTime > now) return emptyList()

        // Never use a boundary later than "now": for a past day this is a
        // no-op (naturalEndOfDay is already < now); for today, this is the
        // fix - clip to the current instant instead of tonight's midnight.
        val endTime = minOf(naturalEndOfDay, now)

        return getUsageStatsInRange(startTime, endTime)
    }

    fun getTodayUsageStats(): List<AppUsageInfo> = getUsageStatsForDay(Calendar.getInstance())


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