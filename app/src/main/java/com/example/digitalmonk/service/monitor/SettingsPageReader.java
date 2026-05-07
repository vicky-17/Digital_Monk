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

    // CHANGED: replaced button texts with body texts visible in accessibility tree
    // MIUI deliberately hides action buttons from accessibility tree as a security measure.
    // We use body text that is always rendered as TextView instead.
    private static final Set<String> DANGER_BUTTONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    // Device Admin page — body text confirmed visible in accessibility dump
                    "This admin app is active",
                    // App Info page — buttons still visible on stock Android
                    "Force stop",
                    "Uninstall",
                    "Storage & cache",
                    "Storage and cache"
            ))
    );

    private volatile boolean escapeInProgress    = true;
    private volatile long    lastEscapeAttemptMs = 0L;

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean readAndRespond(Context context, String settingsPkg) {
        Log.d("MONK_TRACE", "readAndRespond() called → pkg=" + settingsPkg
                + " | escapeInProgress=" + escapeInProgress);

        if (escapeInProgress) {
            Log.d("MONK_TRACE", "readAndRespond() → SKIP: escapeInProgress");
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastEscapeAttemptMs < ESCAPE_COOLDOWN_MS) {
            Log.d("MONK_TRACE", "readAndRespond() → SKIP: cooldown active, remaining="
                    + (ESCAPE_COOLDOWN_MS - (now - lastEscapeAttemptMs)) + "ms");
            return false;
        }

        AccessibilityNodeInfo root = getAccessibilityRoot();
        Log.d("MONK_TRACE", "readAndRespond() → root=" + (root != null ? "AVAILABLE" : "NULL"));

        if ("com.miui.securitycenter".equals(settingsPkg)) {
            if (root != null && isDangerousSettingsPage(root, settingsPkg)) {
                Log.w("MONK_TRACE", "readAndRespond() → DANGEROUS (miui path) → launching redirect");
                lastEscapeAttemptMs = now;
                launchRedirectActivity(context);
                return true;
            }
            Log.d("MONK_TRACE", "readAndRespond() → miui path, not dangerous or root null");
            return false;
        }

        if (isDangerousSettingsPage(root, settingsPkg)) {
            Log.w("MONK_TRACE", "readAndRespond() → DANGEROUS → launching redirect");
            lastEscapeAttemptMs = now;
            launchRedirectActivity(context);
            return true;
        }

        Log.d("MONK_TRACE", "readAndRespond() → safe page");
        return false;
    }

    public void reset() {
        escapeInProgress = false;
        Log.d(TAG, "SettingsPageReader reset");
    }

    // ── Launch redirect activity ──────────────────────────────────────────────

    private void launchRedirectActivity(Context context) {
        escapeInProgress = true;
        try {
            Intent intent = new Intent(context, com.example.digitalmonk.ui.block.BlockedPageActivity.class);
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

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            escapeInProgress = false;
        }, 1500L);
    }

    // ── Detection ─────────────────────────────────────────────────────────────

    private boolean isDangerousSettingsPage(AccessibilityNodeInfo root, String packageName) {
        Log.d("MONK_TRACE", "isDangerousSettingsPage() → pkg=" + packageName + " | root=" + (root != null ? "ok" : "null"));

        if (packageName == null || !SETTINGS_PACKAGES.contains(packageName)) {
            Log.d("MONK_TRACE", "isDangerousSettingsPage() → GATE1 FAIL: not a settings package");
            return false;
        }
        if (root == null) {
            Log.d("MONK_TRACE", "isDangerousSettingsPage() → GATE2 FAIL: root is null");
            return false;
        }
        if (!hasAnyText(root, DANGEROUS_TITLES)) {
            Log.d("MONK_TRACE", "isDangerousSettingsPage() → GATE3 FAIL: no dangerous title found");
            return false;
        }
        if (!hasExactText(root, "Digital Monk")) {
            Log.d("MONK_TRACE", "isDangerousSettingsPage() → GATE4 FAIL: 'Digital Monk' text not found");
            return false;
        }
        // CHANGED: Gate 5 now checks body text instead of buttons
        // MIUI hides action buttons from accessibility tree — confirmed via UI dump
        if (!hasAnyText(root, DANGER_BUTTONS)) {
            Log.d("MONK_TRACE", "isDangerousSettingsPage() → GATE5 FAIL: no confirmation text found (buttons hidden by MIUI)");
            return false;
        }

        Log.w("MONK_TRACE", "isDangerousSettingsPage() → ALL GATES PASSED ✓");
        return true;
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
        long lastEvent  = GuardianAccessibilityService.lastEventTimestamp;
        long now        = System.currentTimeMillis();

        Log.d("MONK_TRACE", "getAccessibilityRoot() → connected=" + connected
                + " | timeSinceConnected=" + (connected > 0 ? (now - connected) : "N/A")
                + " | lastEvent=" + lastEvent
                + " | timeSinceLastEvent=" + (lastEvent > 0 ? (now - lastEvent) : "N/A")
                + " | graceMs=" + ACCESSIBILITY_GRACE_MS);

        if (connected == 0) {
            Log.d("MONK_TRACE", "getAccessibilityRoot() → NULL: service never connected");
            return null;
        }
        if (now - connected < ACCESSIBILITY_GRACE_MS) {
            Log.d("MONK_TRACE", "getAccessibilityRoot() → NULL: still in grace period");
            return null;
        }
        if (lastEvent == 0) {
            Log.d("MONK_TRACE", "getAccessibilityRoot() → NULL: no events received yet");
            return null;
        }
        if (now - lastEvent > 15_000L) {
            Log.d("MONK_TRACE", "getAccessibilityRoot() → NULL: last event too old (" + (now - lastEvent) + "ms ago)");
            return null;
        }

        AccessibilityNodeInfo root = GuardianAccessibilityService.getCurrentRootNode();
        Log.d("MONK_TRACE", "getAccessibilityRoot() → " + (root != null ? "GOT ROOT" : "getCurrentRootNode() returned null"));
        return root;
    }
}