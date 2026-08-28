package com.digitalmonk.app.service.accessibility.handlers

import android.util.Log
import com.digitalmonk.app.data.local.prefs.PrefsManager

/**
 * Why we made this file:
 * While the AppBlockHandler immediately kicks a child out of forbidden apps,
 * the ScreenTimeHandler is responsible for tracking how long a child spends
 * in allowed apps.
 * 
 * It manages the "Session State" (knowing when an app was opened and when it
 * was closed or switched away from) and enforces the daily time limits set
 * by the parent.
 * 
 * What the file name defines:
 * "ScreenTime" specifies the feature being monitored.
 * "Handler" identifies it as a processor delegated by the main Accessibility Service.
 */
class ScreenTimeHandler
/**
 * Constructor injecting the required dependencies.
 * Notice we are reusing the ActionPerformer interface from AppBlockHandler!
 */(
    private val prefs: PrefsManager,
    private val actionPerformer: AppBlockHandler.ActionPerformer?
) {
    // State variables to track the currently active session
    private var currentPackage: String? = null
    private var sessionStartTime = 0L

    /**
     * Called every time the AppOpenDetector senses a window state change.
     */
    fun onAppChanged(newPackageName: String?) {
        if (newPackageName == null) return

        val currentTime = System.currentTimeMillis()

        // If the child was in an app and just switched to a new one
        if (currentPackage != null && currentPackage != newPackageName) {
            val timeSpentMillis = currentTime - sessionStartTime
            Log.d(
                TAG,
                "Time spent in " + currentPackage + ": " + (timeSpentMillis / 1000) + " seconds"
            )

            // TODO: Save this time payload to UsageRepository / Room Database
            // e.g., usageRepository.addTime(currentPackage, timeSpentMillis);
        }

        // Start tracking the new app
        currentPackage = newPackageName
        sessionStartTime = currentTime
    }

    /**
     * A method that should be called periodically (e.g., via a Handler or Timer)
     * while an app is active to see if the child has hit their limit.
     */
    fun enforceTimeLimits() {
        if (currentPackage == null || !prefs.isScreenTimeEnabled) {
            return
        }

        val dailyLimitMinutes = prefs.dailyScreenTimeLimitMinutes
        if (dailyLimitMinutes <= 0) {
            return  // 0 means no limit is set
        }

        // TODO: Fetch total time spent today from UsageRepository
        // long totalTimeSpentToday = usageRepository.getTotalTimeToday(currentPackage);
        // long currentSessionTime = System.currentTimeMillis() - sessionStartTime;

        /* * Example enforcement logic:
         * if ((totalTimeSpentToday + currentSessionTime) > (dailyLimitMinutes * 60 * 1000L)) {
         * Log.w(TAG, "⏱️ Time limit exceeded for " + currentPackage);
         * actionPerformer.performAction(AccessibilityService.GLOBAL_ACTION_HOME);
         * }
         */
    }

    companion object {
        private const val TAG = "ScreenTimeHandler"
    }
}