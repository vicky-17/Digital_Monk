package com.example.digitalmonk.utils;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsageStatsHelper {

    private static UsageStatsManager getUsageStatsManager(Context context) {
        return (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    public static List<Map<String, Object>> getTodayUsageMinutes(Context context) {
        return getUsageStats(context, true);
    }

    public static List<Map<String, Object>> getRawDailyUsageStats(Context context) {
        return getUsageStats(context, false);
    }

    /**
     * UPDATED METHOD: Uses getInstalledApplications + Event-Based Time + Improved Filtering
     */
    private static List<Map<String, Object>> getUsageStats(Context context, boolean filterSystem) {
        PackageManager pm = context.getPackageManager();
        List<Map<String, Object>> result = new ArrayList<>();

        // 1. Get Precise Usage Time (using the Event Logic below)
        Map<String, Long> preciseUsageMap = getEventBasedDailyUsage(context);

        // 2. Get All Installed Apps (Standard approach to get names/icons)
        //    We iterate installed apps instead of UsageStats to ensure we filter correctly.
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);

        for (ApplicationInfo app : apps) {
            String packageName = app.packageName;

            // --- FILTERING LOGIC START ---
            if (filterSystem) {
                boolean isSystem = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean hasLaunchIntent = pm.getLaunchIntentForPackage(packageName) != null;

                // Rule: If it's a System app, it MUST have a Launch Intent (Icon) to be shown.
                // User apps (!isSystem) are always shown.
                if (isSystem && !hasLaunchIntent) {
                    continue;
                }
            }
            // --- FILTERING LOGIC END ---

            // 3. Get Time from our Precise Map
            long totalTimeMs = 0;
            if (preciseUsageMap.containsKey(packageName)) {
                totalTimeMs = preciseUsageMap.get(packageName);
            }

            // Optional: Skip apps with 0 usage if you only want active apps
            if (totalTimeMs == 0) continue;

            long minutes = totalTimeMs / 1000 / 60;

            Map<String, Object> data = new HashMap<>();
            data.put("packageName", packageName);
            data.put("appName", pm.getApplicationLabel(app).toString());
            data.put("minutes", minutes);
            data.put("totalMs", totalTimeMs);

            result.add(data);
        }

        // 4. Sort by Usage Time (Descending)
        Collections.sort(result, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                Long time1 = (Long) o1.get("totalMs");
                Long time2 = (Long) o2.get("totalMs");
                return time2.compareTo(time1); // Descending
            }
        });

        return result;
    }

    /**
     * Calculates usage from Midnight to Now using specific EVENTS.
     */
    public static Map<String, Long> getEventBasedDailyUsage(Context context) {
        UsageStatsManager usm = getUsageStatsManager(context);

        // 1. Calculate Midnight
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long start = calendar.getTimeInMillis();
        long end = System.currentTimeMillis();

        // 2. Query Events
        UsageEvents events = usm.queryEvents(start, end);

        Map<String, Long> totalTimeMap = new HashMap<>();

        if (events == null) {
            return totalTimeMap;
        }

        UsageEvents.Event event = new UsageEvents.Event();

        Map<String, Long> openSessionMap = new HashMap<>();

        // 3. Iterate
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            String pkg = event.getPackageName();

            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                openSessionMap.put(pkg, event.getTimeStamp());
            }
            else if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED ||
                    event.getEventType() == UsageEvents.Event.ACTIVITY_STOPPED) {
                if (openSessionMap.containsKey(pkg)) {
                    long startTime = openSessionMap.get(pkg);
                    long duration = event.getTimeStamp() - startTime;

                    long currentTotal = totalTimeMap.containsKey(pkg) ? totalTimeMap.get(pkg) : 0L;
                    totalTimeMap.put(pkg, currentTotal + duration);
                    openSessionMap.remove(pkg);
                }
            }
        }

        // 4. Handle apps currently open
        for (Map.Entry<String, Long> entry : openSessionMap.entrySet()) {
            String pkg = entry.getKey();
            long startTime = entry.getValue();
            long duration = end - startTime;

            long currentTotal = totalTimeMap.containsKey(pkg) ? totalTimeMap.get(pkg) : 0L;
            totalTimeMap.put(pkg, currentTotal + duration);
        }

        return totalTimeMap;
    }
}