package com.example.digitalmonk.service.monitor;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.digitalmonk.service.accessibility.GuardianAccessibilityService;
import com.example.digitalmonk.service.overlay.SettingsBlockOverlayService;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SettingsPageReader
 * ─────────────────────────────────────────────────────────────────────────────
 * Reads the page content IN REAL TIME once SettingsAppMonitor has confirmed
 * that a settings package is open.
 *
 * Called from WatchdogService's fast-poll loop (every 300ms) ONLY when
 * SettingsAppMonitor.isSettingsOpen() == true.
 *
 * Detection strategy:
 *   1. Try to get root node from GuardianAccessibilityService (if alive).
 *   2. If accessibility is unavailable, skip page reading — stay in BOTTOM mode.
 *      (WatchdogService already has the bottom overlay showing from UsageStats.)
 *   3. If accessibility IS alive, run UninstallerDetector 4-gate check.
 *   4. Report result: DANGEROUS or SAFE, driving overlay expansion/shrink.
 *
 * Why this split?
 *   - UsageStats = reliable foreground detection (always works)
 *   - Accessibility = optional page content reading (best-effort)
 *   The bottom overlay is always shown when settings opens, REGARDLESS of
 *   accessibility state. Full-screen only happens when we can confirm the page.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class SettingsPageReader {

    private static final String TAG = "SettingsPageReader";

    // ── State ─────────────────────────────────────────────────────────────────

    /** Prevents repeated expand/shrink calls on the same page. */
    private boolean lastResultWasDangerous = false;

    /** Prevents reading when accessibility just restarted (gives it 20s grace). */
    private static final long ACCESSIBILITY_GRACE_MS = 20_000L;

    // ── Known settings packages (mirrors SettingsAppMonitor) ─────────────────
    private static final Set<String> SETTINGS_PACKAGES =
            SettingsAppMonitor.SETTINGS_PACKAGES;

    // ── Dangerous page title anchors ──────────────────────────────────────────
    private static final Set<String> DANGEROUS_TITLES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "App info",
                    "Device admin app",
                    "Application info"   // some OEMs use this label
            ))
    );

    // ── Danger button texts ───────────────────────────────────────────────────
    private static final Set<String> DANGER_BUTTONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "Force stop",
                    "Uninstall",
                    "Deactivate & uninstall",
                    "Deactivate and uninstall"
            ))
    );

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called every 300ms while settings is open.
     * Drives overlay expansion or shrink based on page content.
     *
     * @param context          Application context
     * @param settingsPkg      The settings package currently in foreground
     */
    public void readAndRespond(Context context, String settingsPkg) {

        // If locked to full screen, stop all processing immediately
        if (SettingsBlockOverlayService.isFullOverlay) return;

        // Step 1: Try to get root from accessibility service
        AccessibilityNodeInfo root = getAccessibilityRoot();

//        if (root == null) {
//            // Accessibility unavailable — stay in bottom-only mode (already showing)
//            // No expand, no shrink — just wait
//            Log.d(TAG, "Accessibility unavailable — staying in bottom mode");
//            return;
//        }

        // Step 2: Run 4-gate check
        boolean isDangerous = isDangerousSettingsPage(root, settingsPkg);

        // Step 3: Drive overlay state
        if (isDangerous && !lastResultWasDangerous) {
            Log.d("MONK_DEBUG", "PageReader: Dangerous page! Expanding to FULL.");
            Log.w(TAG, "🚨 Dangerous page detected — expanding to full overlay");
            lastResultWasDangerous = true;
            SettingsBlockOverlayService.expandFull(context);

        } else if (!isDangerous && lastResultWasDangerous) {  // add && lastResultWasDangerous
            Log.d("MONK_DEBUG", "PageReader: Safe page. Shrinking to 50dp.");
            lastResultWasDangerous = false;
            SettingsBlockOverlayService.shrinkToBottom(context);
        }
    }

    /**
     * Reset state when settings is closed, so next open starts fresh.
     */
    public void reset() {
        lastResultWasDangerous = false;
    }

    // ── Accessibility root retrieval ──────────────────────────────────────────

    private AccessibilityNodeInfo getAccessibilityRoot() {
        // Check if accessibility service is even connected
        long connected = GuardianAccessibilityService.serviceConnectedTimestamp;
        if (connected == 0) return null;

        // Grace period: don't read immediately after service connects
        if (System.currentTimeMillis() - connected < ACCESSIBILITY_GRACE_MS) {
            return null;
        }

        // Check if it's been receiving events recently (not frozen)
        long lastEvent = GuardianAccessibilityService.lastEventTimestamp;
        if (lastEvent == 0) return null;
        if (System.currentTimeMillis() - lastEvent > 15_000L) {
            Log.w(TAG, "Accessibility appears frozen — skipping page read");
            return null;
        }

        // Ask the service for the current root node
        // We use a static reference to call getRootInActiveWindow indirectly
        // by exposing it from the service
        return GuardianAccessibilityService.getCurrentRootNode();
    }

    // ── 4-Gate detection (same logic as old UninstallerDetector) ─────────────

    /**
     * Returns true ONLY when:
     *   Gate 1 — package is a known settings app
     *   Gate 2 — root node is non-null
     *   Gate 3 — page title is "App info" or "Device admin app"
     *   Gate 4 — "Digital Monk" text is visible
     *   Gate 5 — a danger button (Force stop / Uninstall / Deactivate) is visible
     */
    private boolean isDangerousSettingsPage(AccessibilityNodeInfo root, String packageName) {
        // Gate 1
        if (packageName == null || !SETTINGS_PACKAGES.contains(packageName)) return false;

        // Gate 2
        if (root == null) return false;

        // Gate 3 — page title
        if (!hasAnyText(root, DANGEROUS_TITLES)) return false;

        // Gate 4 — our app name visible
        if (!hasExactText(root, "Digital Monk")) return false;

        // Gate 5 — danger button visible
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
}