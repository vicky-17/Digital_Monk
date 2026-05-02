package com.example.digitalmonk.service.monitor;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.digitalmonk.service.accessibility.GuardianAccessibilityService;
import com.example.digitalmonk.service.overlay.SettingsBlockOverlayService;
import com.example.digitalmonk.service.accessibility.GuardianRedirectActivity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsPageReader {

    private static final String TAG = "SettingsPageReader";

    private static final long ESCAPE_COOLDOWN_MS     = 2_500L;
    private static final long ACCESSIBILITY_GRACE_MS = 5_000L;

    private static final Set<String> SETTINGS_PACKAGES =
            SettingsAppMonitor.SETTINGS_PACKAGES;

    private static final Set<String> DANGEROUS_TITLES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "App info",
                    "Device admin app",
                    "Application info"
            ))
    );

    private static final Set<String> DANGER_BUTTONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "Force stop",
                    "Uninstall",
                    "Deactivate",
                    "Deactivate & uninstall",
                    "Deactivate and uninstall"
            ))
    );

    private volatile boolean escapeInProgress    = false;
    private volatile long    lastEscapeAttemptMs = 0L;

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean  readAndRespond(Context context, String settingsPkg) {
        if (escapeInProgress) return false;

        long now = System.currentTimeMillis();
        if (now - lastEscapeAttemptMs < ESCAPE_COOLDOWN_MS) return false;

        // New: check root first; only skip root check if root is null
        if ("com.miui.securitycenter".equals(settingsPkg)) {
            AccessibilityNodeInfo root = getAccessibilityRoot();
            if (root != null && isDangerousSettingsPage(root, settingsPkg)) {
                Log.w(TAG, "🚨 miui.securitycenter → escape");
                lastEscapeAttemptMs = now;
                launchRedirectActivity(context);
                return true; // ← dangerous, redirected
            }
            return false; // ← safe, just exploring
        }

        AccessibilityNodeInfo root = getAccessibilityRoot();
        if (isDangerousSettingsPage(root, settingsPkg)) {
            Log.w(TAG, "🚨 Dangerous page confirmed in " + settingsPkg);
            lastEscapeAttemptMs = now;
            launchRedirectActivity(context);
            return true; // ← dangerous, redirected
        }
        return false; // ← safe, just exploring

    }

    public void reset() {
        escapeInProgress = false;
        Log.d(TAG, "SettingsPageReader reset");
    }

    // ── Launch redirect activity ──────────────────────────────────────────────

    /**
     * Launches GuardianRedirectActivity which takes over the screen
     * and runs the HOME → RECENTS → CLEAR → HOME sequence.
     *
     * FLAG_ACTIVITY_NEW_TASK         — required from non-Activity context
     * FLAG_ACTIVITY_NO_ANIMATION     — instant switch, no slide animation
     * FLAG_ACTIVITY_CLEAR_TOP        — clears any existing instance
     */
    private void launchRedirectActivity(Context context) {
        escapeInProgress = true;
        try {
            Intent intent = new Intent(context, GuardianRedirectActivity.class);
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION
                            | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                            | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            );
            context.startActivity(intent);
            Log.i(TAG, "GuardianRedirectActivity launched");
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch GuardianRedirectActivity", e);
            escapeInProgress = false;
        }

        // Release escapeInProgress after the full sequence duration
        // GuardianRedirectActivity finishes at ~900ms, add buffer
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            escapeInProgress = false;
        }, 1500L);
    }

    // ── Detection ─────────────────────────────────────────────────────────────

    private boolean isDangerousSettingsPage(AccessibilityNodeInfo root, String packageName) {
        if (packageName == null || !SETTINGS_PACKAGES.contains(packageName)) return false;
        if (root == null) return false;
        if (!hasAnyText(root, DANGEROUS_TITLES)) return false;
        if (!hasExactText(root, "Digital Monk")) return false;
        return hasAnyText(root, DANGER_BUTTONS);
    }

    private boolean hasAnyText(AccessibilityNodeInfo root, Set<String> candidates) {
        for (String c : candidates) {
            if (hasExactText(root, c)) return true;
        }
        return false;
    }

    private boolean hasExactText(AccessibilityNodeInfo root, String text) {
        try {
            if (root == null || text == null) return false;
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
            return nodes != null && !nodes.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private AccessibilityNodeInfo getAccessibilityRoot() {
        long connected = GuardianAccessibilityService.serviceConnectedTimestamp;
        if (connected == 0) return null;
        if (System.currentTimeMillis() - connected < ACCESSIBILITY_GRACE_MS) return null;
        long lastEvent = GuardianAccessibilityService.lastEventTimestamp;
        if (lastEvent == 0) return null;
        if (System.currentTimeMillis() - lastEvent > 15_000L) return null;
        return GuardianAccessibilityService.getCurrentRootNode();
    }
}