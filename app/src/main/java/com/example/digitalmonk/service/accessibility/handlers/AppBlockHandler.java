package com.example.digitalmonk.service.accessibility.handlers;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.monitor.SettingsAppMonitor;

/**
 * AppBlockHandler — Updated
 * ─────────────────────────────────────────────────────────────────────────────
 * REMOVED: All settings page / uninstaller detection logic.
 *          This is now fully owned by WatchdogService + SettingsAppMonitor
 *          + SettingsPageReader + SettingsBlockOverlayService.
 *
 * REMAINS: Standard app blocking — pushes user to home screen if they open
 *          an app that the parent has blocked in PrefsManager.
 *
 * Settings packages are explicitly EXCLUDED from app-block logic to avoid
 * conflicts with the new WatchdogService-driven overlay system.
 * ─────────────────────────────────────────────────────────────────────────────
 */
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

    /**
     * Handles accessibility events for standard app blocking.
     *
     * @param root        Root accessibility node (may be null)
     * @param packageName Current foreground package
     * @param eventType   Accessibility event type
     * @param context     Application context
     */
    public void handle(AccessibilityNodeInfo root,
                       String packageName,
                       int eventType,
                       android.content.Context context) {

        if (packageName == null) return;

        // ── Exclude settings packages — handled by WatchdogService ────────────
        // We do NOT block navigation to settings; SettingsBlockOverlayService
        // covers those pages with an overlay instead of back-press.
        if (SettingsAppMonitor.SETTINGS_PACKAGES.contains(packageName)) {
            return;
        }

        // ── Standard app blocking ─────────────────────────────────────────────
        // Only act on window state changes (new app opened) to avoid
        // spamming GLOBAL_ACTION_HOME on every content update.
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        if (!prefs.isAppBlocked(packageName)) return;

        Log.d(TAG, "🚫 Blocked: " + packageName + " → HOME");
        actionPerformer.performAction(AccessibilityService.GLOBAL_ACTION_HOME);
    }
}