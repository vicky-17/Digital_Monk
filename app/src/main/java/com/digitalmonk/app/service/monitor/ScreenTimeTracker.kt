package com.digitalmonk.app.service.monitor

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

/**
 * Why we made this file:
 * To enforce daily screen time limits, we need an authoritative source of truth
 * for how long the child has used an app today. Android's built-in UsageStatsManager
 * provides this data directly from the OS.
 * 
 * This class acts as a wrapper around that system service. It calculates midnight
 * of the current day and queries the system for all app usage from midnight until now.
 * 
 * What the file name defines:
 * "ScreenTime" specifies the metric being measured.
 * "Tracker" identifies it as the utility responsible for fetching this data.
 */
class ScreenTimeTracker(context: Context) {
    private val context: Context

    init {
        // Using getApplicationContext() prevents memory leaks if a shorter-lived
        // Context (like an Activity) is accidentally passed in.
        this.context = context.getApplicationContext()
    }

    /**
     * Fetches the total time spent in a specific app today (since midnight).
     * 
     * @param packageName The app to check (e.g., "com.zhiliaoapp.musically" for TikTok).
     * @return Total time in milliseconds, or 0 if no usage is found.
     */
    fun getAppUsageToday(packageName: String?): Long {
        if (packageName == null || packageName.isEmpty()) return 0L

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager?
        if (usageStatsManager == null) return 0L

        // Calculate time range: Midnight today to right now
        val endTime = System.currentTimeMillis()
        val startTime = this.midnightToday

        // Query the system for daily usage stats
        val statsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        if (statsList != null) {
            for (stats in statsList) {
                if (packageName == stats.getPackageName()) {
                    return stats.getTotalTimeInForeground()
                }
            }
        }

        return 0L
    }

    val allUsageToday: MutableMap<String?, Long?>
        /**
         * Fetches the usage for ALL apps used today.
         * Useful for your AppUsageWorker when syncing data to the Vercel backend.
         * 
         * @return A map of Package Name -> Time Spent in milliseconds.
         */
        get() {
            val usageMap: MutableMap<String?, Long?> =
                HashMap<String?, Long?>()

            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager?
            if (usageStatsManager == null) return usageMap

            val endTime = System.currentTimeMillis()
            val startTime = this.midnightToday

            val statsList =
                usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, startTime, endTime
                )

            if (statsList != null) {
                for (stats in statsList) {
                    val timeInForeground = stats.getTotalTimeInForeground()
                    if (timeInForeground > 0) {
                        usageMap.put(stats.getPackageName(), timeInForeground)
                    }
                }
            }

            return usageMap
        }

    private val midnightToday: Long
        /**
         * Helper method to calculate the epoch timestamp for 12:00 AM today.
         */
        get() {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.getTimeInMillis()
        }

    companion object {
        private const val TAG = "ScreenTimeTracker"
    }
}