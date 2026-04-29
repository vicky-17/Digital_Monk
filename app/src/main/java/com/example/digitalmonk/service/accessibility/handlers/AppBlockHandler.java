package com.example.digitalmonk.service.accessibility.handlers;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.accessibility.detectors.UninstallerDetector;
import com.example.digitalmonk.service.overlay.SettingsBlockOverlayService;

public class AppBlockHandler {

    private static final String TAG = "AppBlockHandler";
    private static final int GLOBAL_ACTION_HOME = AccessibilityService.GLOBAL_ACTION_HOME;

    private final PrefsManager prefs;
    private final ActionPerformer actionPerformer;

    // ── Debounce: only re-check the uninstaller detector when the package
    //    changes, not on every CONTENT_CHANGED event. ──────────────────────────
    private String lastCheckedPackage = null;
    private boolean lastWasDangerous  = false;

    public interface ActionPerformer {
        boolean performAction(int action);
    }

    public AppBlockHandler(PrefsManager prefs, ActionPerformer actionPerformer) {
        this.prefs = prefs;
        this.actionPerformer = actionPerformer;
    }

    /**
     * Called from GuardianAccessibilityService on every relevant event.
     *
     * @param root        Current window root node (may be null)
     * @param packageName Foreground app package name
     * @param eventType   Accessibility event type (TYPE_WINDOW_STATE_CHANGED or CONTENT_CHANGED)
     * @param context     Android context
     */
    public void handle(AccessibilityNodeInfo root,
                       String packageName,
                       int eventType,
                       android.content.Context context) {

        if (packageName == null) return;

        // ── 1. Block dangerous settings pages (anti-uninstall protection) ────
        //
        // Only re-run the UninstallerDetector on TYPE_WINDOW_STATE_CHANGED
        // (i.e. when the user navigates to a new page/screen).
        // CONTENT_CHANGED fires dozens of times per second (scroll, animation,
        // etc.) and running the detector there causes the "App not responding" ANR.
        //
        boolean runUninstallerCheck =
                (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                        || !packageName.equals(lastCheckedPackage); // package switched mid-session

        if (runUninstallerCheck) {
            lastCheckedPackage = packageName;
            lastWasDangerous   = UninstallerDetector.isDangerousSettingsPage(root, packageName);
        }

        if (lastWasDangerous) {
            if (!SettingsBlockOverlayService.isRunning) {
                Log.w(TAG, "🚨 Dangerous settings page detected — showing overlay for: " + packageName);
                SettingsBlockOverlayService.show(context);
            }
            return; // Don't fall through to standard app-block logic
        }

        // ── 2. Standard app blocking ──────────────────────────────────────────
        if (!prefs.isAppBlocked(packageName)) return;

        Log.d(TAG, "🚫 Blocked app launched: " + packageName + " — sending HOME");
        actionPerformer.performAction(GLOBAL_ACTION_HOME);
    }

    /**
     * Legacy overload for callers that don't pass eventType yet.
     * Treats every call as a STATE_CHANGED (safe but slightly less optimized).
     * Remove once GuardianAccessibilityService is updated to pass eventType.
     */
    public void handle(AccessibilityNodeInfo root,
                       String packageName,
                       android.content.Context context) {
        handle(root, packageName, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, context);
    }
}