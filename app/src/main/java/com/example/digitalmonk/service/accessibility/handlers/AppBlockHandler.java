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

    /**
     * Once we confirm a dangerous page, this latch stays TRUE until the user
     * navigates completely away from all settings packages.
     * We never flip it back to false just because the detector returns false on
     * a subsequent content-change event — that avoids the "overlay flicker" bug.
     */
    private boolean dangerousPageLatched = false;
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
     * Phase 2 — CONFIRMED full overlay (latched):
     *   UninstallerDetector runs its 4-gate check. If it passes, we upgrade to
     *   full-screen AND latch — meaning we never remove the overlay just because a
     *   subsequent event fails the check. The overlay only goes away when the user
     *   taps "Go to Home Screen" (inside SettingsBlockOverlayService) or when they
     *   leave the settings app entirely.
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
                // User navigated away from settings entirely — clear all state
                if (lastSettingsPackage != null) {
                    lastSettingsPackage  = null;
                    dangerousPageLatched = false;
                    lastCheckedPackage   = null;
                    if (SettingsBlockOverlayService.isRunning) {
                        SettingsBlockOverlayService.hide(context);
                    }
                }
            }
        }

        // ── Phase 2: 4-gate dangerous page confirmation (latched) ──────────────
        //
        // KEY CHANGE: if the latch is already set we skip re-running the detector
        // and keep the full overlay up — no more flicker when detector temporarily
        // returns false on a CONTENT_CHANGED event.
        if (dangerousPageLatched) {
            if (!SettingsBlockOverlayService.isFullOverlay) {
                // Ensure full overlay is showing (e.g. service was killed and restarted)
                SettingsBlockOverlayService.show(context);
            }
            return; // Nothing else to do — overlay is locked until user leaves settings
        }

        // Only run the expensive detector on STATE_CHANGED, or when the package changed.
        boolean runCheck = (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                || !packageName.equals(lastCheckedPackage);

        if (runCheck) {
            lastCheckedPackage = packageName;
            boolean isDangerous = UninstallerDetector.isDangerousSettingsPage(root, packageName);

            if (isDangerous) {
                // Latch immediately — we won't un-latch on a subsequent false result
                dangerousPageLatched = true;
                Log.w(TAG, "🚨 Dangerous page confirmed — latching full overlay: " + packageName);
                SettingsBlockOverlayService.show(context);
                return;
            }
            // Not dangerous — bottom blocker auto-timeout will handle cleanup
        }

        // ── Standard app blocking ──────────────────────────────────────────────
        if (!prefs.isAppBlocked(packageName)) return;
        Log.d(TAG, "🚫 Blocked: " + packageName + " → HOME");
        actionPerformer.performAction(GLOBAL_ACTION_HOME);
    }
}