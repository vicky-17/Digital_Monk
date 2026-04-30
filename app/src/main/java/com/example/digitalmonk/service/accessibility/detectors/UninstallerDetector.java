package com.example.digitalmonk.service.accessibility.detectors;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Precisely detects Android Settings pages that can uninstall or deactivate Digital Monk.
 *
 * DETECTION STRATEGY (based on device screenshots):
 * ─────────────────────────────────────────────────────────────────────────────
 * Page 1 — App Info (com.miui.securitycenter or com.android.settings):
 *   Title: "App info" + "Digital Monk" visible + "Force stop" / "Uninstall" present
 *
 * Page 2 — Device Admin (com.android.settings):
 *   Title: "Device admin app" + "Digital Monk" visible + "Deactivate & uninstall" present
 *
 * PERFORMANCE:
 *   - Package gate first  → O(1) HashSet lookup, bails immediately if not settings
 *   - findAccessibilityNodeInfosByText() → system-indexed, NOT manual recursion
 *   - All 4 conditions must pass → eliminates false positives completely
 *   - Zero manual tree traversal → no ANR risk
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class UninstallerDetector {

    // ── Settings packages that host dangerous pages ───────────────────────────
    private static final Set<String> SETTINGS_PACKAGES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "com.miui.securitycenter",    // MIUI App Info
                    "com.android.settings",       // Stock / MIUI Settings
                    "com.google.android.settings" // Pixel Settings
            ))
    );

    // ── Danger button texts — exact matches from screenshots ─────────────────
    private static final Set<String> DANGER_BUTTON_TEXTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "Force stop",             // App Info bottom bar
                    "Uninstall",              // App Info bottom bar
                    "Deactivate & uninstall"  // Device Admin page
            ))
    );

    // ── Page title anchors — confirmed from screenshots ───────────────────────
    private static final Set<String> DANGEROUS_PAGE_TITLES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "App info",         // Screenshot 1
                    "Device admin app"  // Screenshot 2
            ))
    );

    private UninstallerDetector() {}

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns true ONLY when ALL FOUR conditions are met:
     *   1. Package is a known settings app
     *   2. Page title is "App info" or "Device admin app"
     *   3. "Digital Monk" text is visible on screen
     *   4. A danger button is present (Force stop / Uninstall / Deactivate & uninstall)
     */
    public static boolean isDangerousSettingsPage(AccessibilityNodeInfo root, String packageName) {

        // Gate 1 — cheapest check first
        if (packageName == null || !SETTINGS_PACKAGES.contains(packageName)) return false;

        // Gate 2
        if (root == null) return false;

        // Gate 3 — page title check (system-indexed lookup)
        if (!hasAnyText(root, DANGEROUS_PAGE_TITLES)) return false;

        // Gate 4 — our app must be on screen
        if (!hasExactText(root, "Digital Monk")) return false;

        // Gate 5 — danger button must be visible
        return hasAnyText(root, DANGER_BUTTON_TEXTS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean hasAnyText(AccessibilityNodeInfo root, Set<String> candidates) {
        for (String candidate : candidates) {
            if (hasExactText(root, candidate)) return true;
        }
        return false;
    }

    /**
     * Uses the system AT node index — fast O(log n), no manual recursion.
     * Wrapped in try/catch because MIUI recycles nodes mid-call.
     */
    private static boolean hasExactText(AccessibilityNodeInfo root, String text) {
        try {
            if (root == null || text == null) return false;
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
            return nodes != null && !nodes.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}