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

    // ── State ─────────────────────────────────────────────────────────────────
    private String  lastCheckedPackage  = null;
    private boolean lastWasDangerous    = false;
    private String  lastSettingsPackage = null;

    // ── Settings packages that get an INSTANT bottom blocker on entry ─────────
    private static final java.util.Set<String> SETTINGS_PACKAGES =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "com.miui.securitycenter",
                    "com.android.settings",
                    "com.google.android.settings"
            ));

    public interface ActionPerformer {
        boolean performAction(int action);
    }

    public AppBlockHandler(PrefsManager prefs, ActionPerformer actionPerformer) {
        this.prefs = prefs;
        this.actionPerformer = actionPerformer;
    }

    /**
     * TWO-PHASE blocking:
     *
     * Phase 1 — INSTANT bottom cover:
     *   The moment any settings package comes to foreground (TYPE_WINDOW_STATE_CHANGED),
     *   we show a bottom blocker covering the action button area. Zero content analysis.
     *   This fires in <50ms — before the user can even read what page they're on.
     *
     * Phase 2 — CONFIRMED full overlay:
     *   UninstallerDetector runs its 4-gate check. If it passes, upgrade to full-screen.
     *   If it fails, the 3-second auto-timeout removes the bottom blocker silently.
     */
    public void handle(AccessibilityNodeInfo root,
                       String packageName,
                       int eventType,
                       android.content.Context context) {

        if (packageName == null) return;

        // ── Phase 1: Instant block when settings opens ─────────────────────────
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            if (SETTINGS_PACKAGES.contains(packageName)) {
                // New settings page navigated to — show bottom blocker immediately
                if (!packageName.equals(lastSettingsPackage) || !SettingsBlockOverlayService.isRunning) {
                    lastSettingsPackage = packageName;
                    if (!SettingsBlockOverlayService.isRunning) {
                        Log.d(TAG, "⚡ Instant bottom blocker: " + packageName);
                        SettingsBlockOverlayService.showInstant(context);
                    }
                }
            } else {
                // Left settings entirely — clear state and hide overlays
                if (lastSettingsPackage != null) {
                    lastSettingsPackage = null;
                    lastWasDangerous    = false;
                    lastCheckedPackage  = null;
                    if (SettingsBlockOverlayService.isRunning) {
                        SettingsBlockOverlayService.hide(context);
                    }
                }
            }
        }

        // ── Phase 2: 4-gate dangerous page confirmation ────────────────────────
        // Only on STATE_CHANGED to avoid ANR from CONTENT_CHANGED spam.
        boolean runCheck = (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                || !packageName.equals(lastCheckedPackage);

        if (runCheck) {
            lastCheckedPackage = packageName;
            lastWasDangerous   = UninstallerDetector.isDangerousSettingsPage(root, packageName);
        }

        if (lastWasDangerous) {
            // Upgrade bottom blocker → full screen overlay
            if (!SettingsBlockOverlayService.isFullOverlay) {
                Log.w(TAG, "🚨 Dangerous page confirmed — full overlay: " + packageName);
                SettingsBlockOverlayService.show(context);
            }
            return;
        }

        // ── Standard app blocking ──────────────────────────────────────────────
        if (!prefs.isAppBlocked(packageName)) return;
        Log.d(TAG, "🚫 Blocked: " + packageName + " → HOME");
        actionPerformer.performAction(GLOBAL_ACTION_HOME);
    }

    /** Legacy overload. */
    public void handle(AccessibilityNodeInfo root, String packageName, android.content.Context context) {
        handle(root, packageName, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, context);
    }
}