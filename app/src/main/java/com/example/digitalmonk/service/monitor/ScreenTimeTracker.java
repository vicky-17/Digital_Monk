package com.example.digitalmonk.service.monitor;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.util.Log;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
public class ScreenTimeTracker {

    private static final String TAG = "ScreenTimeTracker";
    private final Context context;

    public ScreenTimeTracker(Context context) {
        // Using getApplicationContext() prevents memory leaks if a shorter-lived
        // Context (like an Activity) is accidentally passed in.
        this.context = context.getApplicationContext();
    }

    /**
     * Fetches the total time spent in a specific app today (since midnight).
     *
     * @param packageName The app to check (e.g., "com.zhiliaoapp.musically" for TikTok).
     * @return Total time in milliseconds, or 0 if no usage is found.
     */
    public long getAppUsageToday(String packageName) {
        if (packageName == null || packageName.isEmpty()) return 0L;

        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) return 0L;

        // Calculate time range: Midnight today to right now
        long endTime = System.currentTimeMillis();
        long startTime = getMidnightToday();

        // Query the system for daily usage stats
        List<UsageStats> statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

        if (statsList != null) {
            for (UsageStats stats : statsList) {
                if (packageName.equals(stats.getPackageName())) {
                    return stats.getTotalTimeInForeground();
                }
            }
        }

        return 0L;
    }

    /**
     * Fetches the usage for ALL apps used today.
     * Useful for your AppUsageWorker when syncing data to the Vercel backend.
     *
     * @return A map of Package Name -> Time Spent in milliseconds.
     */
    public Map<String, Long> getAllUsageToday() {
        Map<String, Long> usageMap = new HashMap<>();

        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) return usageMap;

        long endTime = System.currentTimeMillis();
        long startTime = getMidnightToday();

        List<UsageStats> statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

        if (statsList != null) {
            for (UsageStats stats : statsList) {
                long timeInForeground = stats.getTotalTimeInForeground();
                if (timeInForeground > 0) {
                    usageMap.put(stats.getPackageName(), timeInForeground);
                }
            }
        }

        return usageMap;
    }

    /**
     * Helper method to calculate the epoch timestamp for 12:00 AM today.
     */
    private long getMidnightToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}