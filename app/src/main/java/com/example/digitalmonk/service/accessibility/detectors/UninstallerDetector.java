package com.example.digitalmonk.service.accessibility.detectors;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Specialized detector to identify Android Settings pages that allow
 * a user to uninstall the app or deactivate Device Admin privileges.
 */
public class UninstallerDetector {

    // ── Settings packages that attempt to expose uninstall / deactivate ──────
    private static final Set<String> DANGEROUS_SETTINGS_PACKAGES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "com.miui.securitycenter",       // MIUI App Info (has Uninstall button)
            "com.android.settings",          // Stock Android Settings
            "com.google.android.settings"    // Pixel/Pure Android Settings
    )));

    // Screen titles/content keywords that indicate a dangerous action is possible
    private static final Set<String> DANGEROUS_SCREEN_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "uninstall",
            "deactivate",
            "clear data",
            "force stop",
            "remove device admin"
    )));

    // FIXED: Constructor name must match the Class name
    private UninstallerDetector() {}

    /**
     * Checks if the current screen is a settings page that could lead to app removal.
     */
    public static boolean isDangerousSettingsPage(AccessibilityNodeInfo rootNode, String packageName) {
        if (packageName == null) return false;
        if (!DANGEROUS_SETTINGS_PACKAGES.contains(packageName)) return false;

        if (rootNode == null) return false;

        // Check if the current settings page is actually for OUR app
        boolean isOurAppPage = containsText(rootNode, "Digital Monk");

        // Only proceed to check for dangerous buttons if we are on the Digital Monk page
        if (isOurAppPage) {
            return containsDangerousText(rootNode);
        }

        return false;
    }

    /**
     * Helper to check if a specific string (like our App Name) exists in the UI tree.
     */
    private static boolean containsText(AccessibilityNodeInfo node, String target) {
        if (node == null) return false;

        CharSequence text = node.getText();
        if (text != null && text.toString().equalsIgnoreCase(target)) {
            return true;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (containsText(node.getChild(i), target)) return true;
        }
        return false;
    }

    private static boolean containsDangerousText(AccessibilityNodeInfo node) {
        if (node == null) return false;

        // Check primary text
        CharSequence text = node.getText();
        if (text != null) {
            String lower = text.toString().toLowerCase();
            for (String keyword : DANGEROUS_SCREEN_KEYWORDS) {
                if (lower.contains(keyword)) return true;
            }
        }

        // Check content descriptions (often used for headers/icons)
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String lower = desc.toString().toLowerCase();
            for (String keyword : DANGEROUS_SCREEN_KEYWORDS) {
                if (lower.contains(keyword)) return true;
            }
        }

        // Recurse through children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            try {
                if (containsDangerousText(child)) return true;
            } finally {
                // Important: node info objects must be recycled in a real service,
                // but since we are traversing a tree passed by the service,
                // we just ensure we don't crash.
            }
        }

        return false;
    }
}