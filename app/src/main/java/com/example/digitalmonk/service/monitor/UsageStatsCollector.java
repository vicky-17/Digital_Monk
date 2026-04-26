package com.example.digitalmonk.service.monitor;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Why we made this file:
 * This class is responsible for collecting highly granular app usage data. It parses
 * raw system events (MOVE_TO_FOREGROUND and MOVE_TO_BACKGROUND) and stitches them
 * together into complete "Sessions".
 *
 * What the file name defines:
 * "UsageStats" refers to Android's built-in usage tracking framework.
 * "Collector" signifies its role in gathering and assembling raw data into useful objects.
 */
public class UsageStatsCollector {

    private static final String TAG = "UsageStatsCollector";
    private final Context context;

    public UsageStatsCollector(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * A simple POJO representing a single block of time spent in an app.
     * This structured data is perfect for converting to JSON for your MongoDB database.
     */
    public static class AppSession {
        public String packageName;
        public long startTime;
        public long endTime;

        public AppSession(String packageName, long startTime) {
            this.packageName = packageName;
            this.startTime = startTime;
            this.endTime = 0L; // Will be set when the app moves to the background
        }

        public long getDurationMillis() {
            return endTime - startTime;
        }
    }

    /**
     * Queries the Android system for raw events and stitches them into complete sessions.
     *
     * @param startTime Epoch timestamp to start searching from.
     * @param endTime Epoch timestamp to end searching at.
     * @return A list of completed app usage sessions.
     */
    public List<AppSession> getUsageSessions(long startTime, long endTime) {
        List<AppSession> completedSessions = new ArrayList<>();

        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) return completedSessions;

        // queryEvents gives us the exact chronological timeline of device activity
        UsageEvents events = usageStatsManager.queryEvents(startTime, endTime);
        UsageEvents.Event currentEvent = new UsageEvents.Event();

        // We use a Map to keep track of apps that are currently open (foregrounded)
        // but haven't been closed (backgrounded) yet.
        Map<String, AppSession> activeSessions = new HashMap<>();

        while (events.hasNextEvent()) {
            events.getNextEvent(currentEvent);
            String packageName = currentEvent.getPackageName();
            long timestamp = currentEvent.getTimeStamp();

            if (packageName == null) continue;

            int eventType = currentEvent.getEventType();

            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                // App came to the foreground — start a new session
                activeSessions.put(packageName, new AppSession(packageName, timestamp));

            } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                    eventType == UsageEvents.Event.ACTIVITY_STOPPED) {

                // App went to the background — finish the session
                AppSession session = activeSessions.remove(packageName);

                if (session != null) {
                    session.endTime = timestamp;
                    // Only save sessions that lasted longer than 1 second to filter out system noise
                    if (session.getDurationMillis() > 1000) {
                        completedSessions.add(session);
                    }
                }
            }
        }

        // Note: Any sessions still left in 'activeSessions' map mean the app was
        // still open when 'endTime' was reached.

        Log.d(TAG, "Collected " + completedSessions.size() + " completed app sessions.");
        return completedSessions;
    }
}