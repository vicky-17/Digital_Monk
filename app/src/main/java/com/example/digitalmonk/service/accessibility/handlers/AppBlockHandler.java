package com.example.digitalmonk.service.accessibility.handlers;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.monitor.SettingsAppMonitor;

public class AppBlockHandler {

    private static final String TAG = "AppBlockHandler";

    private final PrefsManager   prefs;
    private final ActionPerformer actionPerformer;

    public interface ActionPerformer {
        boolean performAction(int action);
    }

    public AppBlockHandler(PrefsManager prefs, ActionPerformer actionPerformer) {
        this.prefs           = prefs;
        this.actionPerformer = actionPerformer;
    }

    public void handle(AccessibilityNodeInfo root,
                       String packageName,
                       int eventType,
                       android.content.Context context) {

        if (packageName == null) return;

        if (SettingsAppMonitor.SETTINGS_PACKAGES.contains(packageName)) {
            return;
        }

        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        if (!prefs.isAppBlocked(packageName)) return;

        Log.d(TAG, "🚫 Blocked: " + packageName + " → HOME");
        actionPerformer.performAction(AccessibilityService.GLOBAL_ACTION_HOME);
    }
}