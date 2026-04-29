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
 * DETECTION STRATEGY (from screenshots):
 * ─────────────────────────────────────────────────────────────────────────────
 * Page 1 — App Info (com.miui.securitycenter or com.android.settings):
 *   Condition: Page title contains "App info" AND "Digital Monk" is visible
 *              AND bottom bar has "Force stop" or "Uninstall" buttons.
 *
 * Page 2 — Device Admin (com.android.settings):
 *   Condition: Page title contains "Device admin app" AND "Digital Monk" is visible
 *              AND "Deactivate & uninstall" button is present.
 *
 * PERFORMANCE RULES (fixes ANR / "app not responding"):
 *   1. Package gate first  — bail instantly if not a settings package.
 *   2. Use findAccessibilityNodeInfosByText() — system-indexed, NOT manual recursion.
 *   3. ALL 4 conditions must pass — eliminates false positives.
 *   4. Zero manual tree recursion — no depth loops anywhere.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class UninstallerDetector {

    // ── Packages that host dangerous pages ───────────────────────────────────
    private static final Set<String> SETTINGS_PACKAGES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "com.miui.securitycenter",    // MIUI App Info
                    "com.android.settings",       // Stock / MIUI Settings
                    "com.google.android.settings" // Pixel Settings
            ))
    );

    // ── Exact button texts that signal a dangerous action ─────────────────────
    // Matches the bottom-bar buttons seen in the screenshots exactly.
    private static final Set<String> DANGER_BUTTON_TEXTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "Force stop",             // App Info bottom bar (screenshot 1)
                    "Uninstall",              // App Info bottom bar (screenshot 1)
                    "Deactivate & uninstall"  // Device Admin page   (screenshot 2)
            ))
    );

    // ── Page-level title anchors ──────────────────────────────────────────────
    // Confirmed from the two screenshots.
    private static final Set<String> DANGEROUS_PAGE_TITLES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "App info",          // Screenshot 1 top-bar title
                    "Device admin app"   // Screenshot 2 top-bar title
            ))
    );

    private UninstallerDetector() {}

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns true ONLY when ALL FOUR conditions are met:
     *   1. We are inside a known settings package
     *   2. The page title is "App info" or "Device admin app"
     *   3. "Digital Monk" text is visible on screen
     *   4. A dangerous action button is present (Force stop / Uninstall / Deactivate & uninstall)
     *
     * This strict 4-gate approach prevents the overlay from showing on any
     * other settings page or during normal app usage.
     */
    public static boolean isDangerousSettingsPage(AccessibilityNodeInfo root, String packageName) {

        // ── Gate 1: Package check (cheapest, do first) ────────────────────────
        if (packageName == null || !SETTINGS_PACKAGES.contains(packageName)) {
            return false;
        }

        // ── Gate 2: Root node check ───────────────────────────────────────────
        if (root == null) {
            return false;
        }

        // ── Gate 3: Confirm page title ────────────────────────────────────────
        // findAccessibilityNodeInfosByText() uses the AT system's node index.
        // It is O(log n) and does NOT block the main thread like manual recursion.
        if (!hasAnyText(root, DANGEROUS_PAGE_TITLES)) {
            return false;
        }

        // ── Gate 4: "Digital Monk" must be on screen ──────────────────────────
        if (!hasExactText(root, "Digital Monk")) {
            return false;
        }

        // ── Gate 5: A danger button must be visible ───────────────────────────
        return hasAnyText(root, DANGER_BUTTON_TEXTS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns true if ANY string from the given set is found on screen.
     * Uses the system-indexed findAccessibilityNodeInfosByText() — fast and safe.
     */
    private static boolean hasAnyText(AccessibilityNodeInfo root, Set<String> candidates) {
        for (String candidate : candidates) {
            if (hasExactText(root, candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the given text appears anywhere in the current window.
     *
     * Note: findAccessibilityNodeInfosByText() does a SUBSTRING match internally,
     * so "App info" will also match "App information". This is fine for our use
     * case since these page titles are very specific.
     *
     * Wraps in try/catch because AT nodes can be recycled mid-call on MIUI.
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