package com.example.digitalmonk.service.accessibility.detectors;

import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Why we made this file:
 * In a parental control app, you need to know exactly when a child switches
 * from an educational app (like Calculator) to an entertainment app (like Instagram).
 * * Because background background limits are strict in modern Android, the most
 * reliable way to detect when a new app is opened on the screen is by using an
 * AccessibilityService. This specific class is a helper designed to parse
 * accessibility events and determine which app package is currently in the foreground.
 *
 * What the file name defines:
 * "AppOpen" refers to the action of an application coming to the foreground.
 * "Detector" signifies its role as an observer/analyzer of system events.
 */
public class AppOpenDetector {

    private static final String TAG = "AppOpenDetector";

    /**
     * Private constructor to prevent instantiation.
     * In Kotlin, the 'object' keyword automatically created a Singleton utility.
     * In Java, we achieve this by making the constructor private and all methods static.
     */
    private AppOpenDetector() {}

    /**
     * Example method you will likely need: Extracts the package name when the
     * window state changes (which usually means a new app was opened).
     */
    public static String getForegroundPackage(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return null;
        }

        // TYPE_WINDOW_STATE_CHANGED is the most reliable event for detecting app switches
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = event.getPackageName().toString();
            Log.d(TAG, "App Opened / Window Changed: " + packageName);
            return packageName;
        }

        return null;
    }

    // TODO: Add further logic to filter out system UI events (like the notification shade opening)
}