package com.example.digitalmonk.service.accessibility.handlers;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;

import com.example.digitalmonk.data.local.prefs.PrefsManager;

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
public class ScreenTimeHandler {

    private static final String TAG = "ScreenTimeHandler";

    private final PrefsManager prefs;
    private final AppBlockHandler.ActionPerformer actionPerformer;

    // State variables to track the currently active session
    private String currentPackage = null;
    private long sessionStartTime = 0L;

    /**
     * Constructor injecting the required dependencies.
     * Notice we are reusing the ActionPerformer interface from AppBlockHandler!
     */
    public ScreenTimeHandler(PrefsManager prefs, AppBlockHandler.ActionPerformer actionPerformer) {
        this.prefs = prefs;
        this.actionPerformer = actionPerformer;
    }

    /**
     * Called every time the AppOpenDetector senses a window state change.
     */
    public void onAppChanged(String newPackageName) {
        if (newPackageName == null) return;

        long currentTime = System.currentTimeMillis();

        // If the child was in an app and just switched to a new one
        if (currentPackage != null && !currentPackage.equals(newPackageName)) {
            long timeSpentMillis = currentTime - sessionStartTime;
            Log.d(TAG, "Time spent in " + currentPackage + ": " + (timeSpentMillis / 1000) + " seconds");

            // TODO: Save this time payload to UsageRepository / Room Database
            // e.g., usageRepository.addTime(currentPackage, timeSpentMillis);
        }

        // Start tracking the new app
        currentPackage = newPackageName;
        sessionStartTime = currentTime;
    }

    /**
     * A method that should be called periodically (e.g., via a Handler or Timer)
     * while an app is active to see if the child has hit their limit.
     */
    public void enforceTimeLimits() {
        if (currentPackage == null || !prefs.isScreenTimeEnabled()) {
            return;
        }

        int dailyLimitMinutes = prefs.getDailyScreenTimeLimitMinutes();
        if (dailyLimitMinutes <= 0) {
            return; // 0 means no limit is set
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
}