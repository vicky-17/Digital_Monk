package com.digitalmonk.app.service.accessibility.detectors;

import android.view.accessibility.AccessibilityNodeInfo;

import com.digitalmonk.app.service.accessibility.AllowlistManager;

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
    public static boolean isDangerousSettingsPage(AccessibilityNodeInfo root, String packageName, boolean antiUninstallEnabled) {

        if (!antiUninstallEnabled) return false;

        // Gate 1
        if (packageName == null || !SETTINGS_PACKAGES.contains(packageName)) return false;
        if (root == null) return false;

        // ── ALLOWLIST CHECK — before any other gate ───────────────────────────
        // If the parent intentionally opened this settings page, let it through.
        AllowlistManager allowlist = AllowlistManager.getInstance();
        if (allowlist.isAnyAllowed(getPageTitleText(root))) {
            return false; // Parent-initiated navigation — don't block
        }

        // Gate 3 — page title
        if (!hasAnyText(root, DANGEROUS_PAGE_TITLES)) return false;

        // Gate 4 — our app name must be visible
        if (!hasExactText(root, "Digital Monk")) return false;

        // Gate 5 — confirmation anchor
        return hasAnyText(root, DANGER_CONFIRM_TEXTS);
    }

    /** Extracts visible title text to check against allowlist keywords. */
    private static String getPageTitleText(AccessibilityNodeInfo root) {
        try {
            // Try common title view IDs first
            for (String titleId : Arrays.asList(
                    "com.android.settings:id/action_bar_title",
                    "android:id/title",
                    "com.miui.securitycenter:id/title"
            )) {
                List<AccessibilityNodeInfo> nodes =
                        root.findAccessibilityNodeInfosByViewId(titleId);
                if (nodes != null && !nodes.isEmpty()) {
                    CharSequence text = nodes.get(0).getText();
                    if (text != null) return text.toString();
                }
            }
            // Fallback: dump all text from the page
            if (root.getText() != null) return root.getText().toString();
        } catch (Exception ignored) {}
        return "";
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