package com.example.digitalmonk.service.accessibility.handlers;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.digitalmonk.data.local.prefs.PrefsManager;

/**
 * Why we made this file:
 * This class handles the specific logic for Phase 2 of Digital Monk: blocking apps.
 * Instead of cluttering the main GuardianAccessibilityService with blocking rules,
 * we delegate the responsibility to this "Handler."
 * * When a blocked app is detected, this handler immediately triggers a system-level
 * "HOME" action, kicking the child out of the app before they can use it.
 *
 * What the file name defines:
 * "AppBlock" defines the feature it handles.
 * "Handler" is an architectural term for a class that processes a specific task
 * delegated from a main service.
 */
public class AppBlockHandler {

    private static final String TAG = "AppBlockHandler";
    // We can use the official Android constant instead of hardcoding '2'
    private static final int GLOBAL_ACTION_HOME = AccessibilityService.GLOBAL_ACTION_HOME;

    private final PrefsManager prefs;
    private final ActionPerformer actionPerformer;

    /**
     * In Kotlin, you passed a function as a parameter: `(Int) -> Boolean`.
     * Java handles this using a "Functional Interface".
     */
    public interface ActionPerformer {
        boolean performAction(int action);
    }

    /**
     * Constructor for Dependency Injection
     */
    public AppBlockHandler(PrefsManager prefs, ActionPerformer actionPerformer) {
        this.prefs = prefs;
        this.actionPerformer = actionPerformer;
    }

    /**
     * Checks if the opened app is blocked, and if so, sends the user Home.
     */
    public void handle(AccessibilityNodeInfo rootNode, String packageName) {
        // Safe check for null package names
        if (packageName == null || !prefs.isAppBlocked(packageName)) {
            return;
        }

        Log.d(TAG, "🚫 Blocked app launched: " + packageName + " — sending HOME");
        actionPerformer.performAction(GLOBAL_ACTION_HOME);

        // TODO Phase 2:
        // - Wire overlay service to show a "This app is blocked" message before going home.
        // - Read blocked packages from Room DB (AppRuleDao) instead of SharedPrefs
        //   for real-time updates without service restart.
    }
}