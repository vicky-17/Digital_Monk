package com.example.digitalmonk.service.accessibility.detectors;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UninstallerDetector {

    // ── Settings packages that host dangerous pages ───────────────────────────
    private static final Set<String> SETTINGS_PACKAGES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "com.miui.securitycenter",
                    "com.android.settings",
                    "com.google.android.settings"
            ))
    );

    // ── Page title anchors ────────────────────────────────────────────────────
    private static final Set<String> DANGEROUS_PAGE_TITLES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "App info",
                    "Application info",
                    "Device admin app"
            ))
    );

    // ── Confirmation anchors — text visible on page that confirms danger ──────
    // Used instead of buttons (MIUI hides buttons from accessibility tree)
    private static final Set<String> DANGER_CONFIRM_TEXTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    // Device Admin page — always present when admin is active
                    "This admin app is active",
                    // App Info page — always present
                    "Force stop",        // still try — stock Android shows it
                    "Uninstall",         // still try — stock Android shows it
                    "Storage & cache",   // unique to App Info page
                    "Storage and cache"  // alternate phrasing
            ))
    );

    private UninstallerDetector() {}

    /**
     * Returns true when ALL conditions are met:
     *   1. Package is a known settings app
     *   2. Root node is available
     *   3. Page title is a known dangerous title
     *   4. "Digital Monk" text is visible on screen
     *   5. A confirmation anchor text is present
     *      (buttons are hidden on MIUI — we use body text instead)
     */
    public static boolean isDangerousSettingsPage(AccessibilityNodeInfo root, String packageName) {

        // Gate 1 — cheapest check first
        if (packageName == null || !SETTINGS_PACKAGES.contains(packageName)) return false;

        // Gate 2
        if (root == null) return false;

        // Gate 3 — page title
        if (!hasAnyText(root, DANGEROUS_PAGE_TITLES)) return false;

        // Gate 4 — our app name must be visible
        if (!hasExactText(root, "Digital Monk")) return false;

        // Gate 5 — confirm we are on the right page via body text
        // (MIUI hides action buttons from accessibility tree — use visible body text instead)
        return hasAnyText(root, DANGER_CONFIRM_TEXTS);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static boolean hasAnyText(AccessibilityNodeInfo root, Set<String> candidates) {
        for (String candidate : candidates) {
            if (hasExactText(root, candidate)) return true;
        }
        return false;
    }

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