package com.curbme.app.service.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log

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
class UsageStatsCollector(context: Context) {
    private val context: Context

    init {
        this.context = context.getApplicationContext()
    }

    /**
     * A simple POJO representing a single block of time spent in an app.
     * This structured data is perfect for converting to JSON for your MongoDB database.
     */
    class AppSession // Will be set when the app moves to the background
        (var packageName: String?, var startTime: Long) {
        var endTime: Long = 0L

        val durationMillis: Long
            get() = endTime - startTime
    }

    /**
     * Queries the Android system for raw events and stitches them into complete sessions.
     * 
     * @param startTime Epoch timestamp to start searching from.
     * @param endTime Epoch timestamp to end searching at.
     * @return A list of completed app usage sessions.
     */
    fun getUsageSessions(startTime: Long, endTime: Long): MutableList<AppSession?> {
        val completedSessions: MutableList<AppSession?> = ArrayList<AppSession?>()

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager?
        if (usageStatsManager == null) return completedSessions

        // queryEvents gives us the exact chronological timeline of device activity
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val currentEvent = UsageEvents.Event()

        // We use a Map to keep track of apps that are currently open (foregrounded)
        // but haven't been closed (backgrounded) yet.
        val activeSessions: MutableMap<String?, AppSession?> = HashMap<String?, AppSession?>()

        while (events.hasNextEvent()) {
            events.getNextEvent(currentEvent)
            val packageName = currentEvent.getPackageName()
            val timestamp = currentEvent.getTimeStamp()

            if (packageName == null) continue

            val eventType = currentEvent.getEventType()

            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                // App came to the foreground — start a new session
                activeSessions.put(packageName, AppSession(packageName, timestamp))
            } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                eventType == UsageEvents.Event.ACTIVITY_STOPPED
            ) {
                // App went to the background — finish the session

                val session = activeSessions.remove(packageName)

                if (session != null) {
                    session.endTime = timestamp
                    // Only save sessions that lasted longer than 1 second to filter out system noise
                    if (session.durationMillis > 1000) {
                        completedSessions.add(session)
                    }
                }
            }
        }

        // Note: Any sessions still left in 'activeSessions' map mean the app was
        // still open when 'endTime' was reached.
        Log.d(TAG, "Collected " + completedSessions.size + " completed app sessions.")
        return completedSessions
    }

    companion object {
        private const val TAG = "UsageStatsCollector"
    }
}