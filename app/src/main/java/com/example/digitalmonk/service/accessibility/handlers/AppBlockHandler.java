package com.example.digitalmonk.service.accessibility.handlers;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.accessibility.detectors.UninstallerDetector;
import com.example.digitalmonk.service.overlay.SettingsBlockOverlayService;

public class AppBlockHandler {

    private static final String TAG = "AppBlockHandler";
    private static final int GLOBAL_ACTION_HOME = AccessibilityService.GLOBAL_ACTION_HOME;

    private final PrefsManager prefs;
    private final ActionPerformer actionPerformer;

    public interface ActionPerformer {
        boolean performAction(int action);
    }

    public AppBlockHandler(PrefsManager prefs, ActionPerformer actionPerformer) {
        this.prefs = prefs;
        this.actionPerformer = actionPerformer;
    }

    public void handle(AccessibilityNodeInfo rootNode, String packageName, android.content.Context context) {
        if (packageName == null) return;

        // ── 1. Block dangerous settings pages (anti-uninstall protection) ────
        if (UninstallerDetector.isDangerousSettingsPage(rootNode, packageName)) {
            if (!SettingsBlockOverlayService.isRunning) {
                Log.w(TAG, "🚨 Dangerous settings page detected — showing overlay");
                SettingsBlockOverlayService.show(context);
            }
            return;
        }

        // ── 2. Standard app blocking ──────────────────────────────────────────
        if (!prefs.isAppBlocked(packageName)) return;

        Log.d(TAG, "🚫 Blocked app launched: " + packageName + " — sending HOME");
        actionPerformer.performAction(GLOBAL_ACTION_HOME);
    }
}