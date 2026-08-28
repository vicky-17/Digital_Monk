package com.digitalmonk.app.service.accessibility.handlers;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.digitalmonk.app.data.local.prefs.PrefsManager;
import com.digitalmonk.app.service.accessibility.detectors.ShortsDetector;

public class ShortsBlockHandler {

    private static final String TAG = "ShortsBlockHandler";
    // We use BACK (1) instead of HOME (2) so we don't close the entire app
    private static final int GLOBAL_ACTION_BACK = AccessibilityService.GLOBAL_ACTION_BACK;

    private final PrefsManager prefs;
    private final ActionPerformer actionPerformer;

    // State variable to prevent spamming the system logs
    private String lastBlockedPackage = null;

    /**
     * Functional interface for the callback. You can also reuse the exact same
     * interface we defined in AppBlockHandler if you prefer.
     */
    public interface ActionPerformer {
        boolean performAction(int action);
    }

    public ShortsBlockHandler(PrefsManager prefs, ActionPerformer actionPerformer) {
        this.prefs = prefs;
        this.actionPerformer = actionPerformer;
    }

    /**
     * Evaluates the current screen state and fires a back press if short-form
     * video is detected.
     */
    public void handle(AccessibilityNodeInfo rootNode, String packageName) {
        if (packageName == null || !prefs.isBlockShorts()) {
            return;
        }

        boolean shouldBlock = ShortsDetector.shouldBlock(rootNode, packageName);

        if (shouldBlock) {
            // Log only if it's a new block event to prevent logcat flooding
            if (!packageName.equals(lastBlockedPackage)) {
                // Note: I swapped your custom Logger for standard Android Log,
                // but you can change it back if you converted Logger.kt to Java!
                Log.d(TAG, "🚫 Blocking Shorts in: " + packageName);
                lastBlockedPackage = packageName;
            }

            // Simulate pressing the physical/digital "Back" button
            actionPerformer.performAction(GLOBAL_ACTION_BACK);
        } else {
            if (packageName.equals(lastBlockedPackage)) {
                lastBlockedPackage = null;
            }
        }
    }
}