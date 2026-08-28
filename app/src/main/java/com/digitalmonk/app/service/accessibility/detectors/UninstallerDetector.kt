package com.digitalmonk.app.service.accessibility.detectors

import android.view.accessibility.AccessibilityNodeInfo
import com.digitalmonk.app.service.accessibility.AllowlistManager
import java.util.Collections

object UninstallerDetector {
    // ── Settings packages that host dangerous pages ───────────────────────────
    private val SETTINGS_PACKAGES: MutableSet<String?> = Collections.unmodifiableSet<String?>(
        HashSet<String?>(
            mutableListOf<String?>(
                "com.miui.securitycenter",
                "com.android.settings",
                "com.google.android.settings"
            )
        )
    )

    // ── Page title anchors ────────────────────────────────────────────────────
    private val DANGEROUS_PAGE_TITLES: MutableSet<String?> = Collections.unmodifiableSet<String?>(
        HashSet<String?>(
            mutableListOf<String?>(
                "App info",
                "Application info",
                "Device admin app"
            )
        )
    )

    // ── Confirmation anchors — text visible on page that confirms danger ──────
    // Used instead of buttons (MIUI hides buttons from accessibility tree)
    private val DANGER_CONFIRM_TEXTS: MutableSet<String?> = Collections.unmodifiableSet<String?>(
        HashSet<String?>(
            mutableListOf<String?>( // Device Admin page — always present when admin is active
                "This admin app is active",  // App Info page — always present
                "Force stop",  // still try — stock Android shows it
                "Uninstall",  // still try — stock Android shows it
                "Storage & cache",  // unique to App Info page
                "Storage and cache" // alternate phrasing
            )
        )
    )

    /**
     * Returns true when ALL conditions are met:
     * 1. Package is a known settings app
     * 2. Root node is available
     * 3. Page title is a known dangerous title
     * 4. "Digital Monk" text is visible on screen
     * 5. A confirmation anchor text is present
     * (buttons are hidden on MIUI — we use body text instead)
     */
    fun isDangerousSettingsPage(
        root: AccessibilityNodeInfo?,
        packageName: String?,
        antiUninstallEnabled: Boolean
    ): Boolean {
        if (!antiUninstallEnabled) return false

        // Gate 1
        if (packageName == null || !SETTINGS_PACKAGES.contains(packageName)) return false
        if (root == null) return false

        // ── ALLOWLIST CHECK — before any other gate ───────────────────────────
        // If the parent intentionally opened this settings page, let it through.
        val allowlist = AllowlistManager.getInstance()
        if (allowlist.isAnyAllowed(getPageTitleText(root))) {
            return false // Parent-initiated navigation — don't block
        }

        // Gate 3 — page title
        if (!hasAnyText(root, DANGEROUS_PAGE_TITLES)) return false

        // Gate 4 — our app name must be visible
        if (!hasExactText(root, "Digital Monk")) return false

        // Gate 5 — confirmation anchor
        return hasAnyText(root, DANGER_CONFIRM_TEXTS)
    }

    /** Extracts visible title text to check against allowlist keywords.  */
    private fun getPageTitleText(root: AccessibilityNodeInfo): String {
        try {
            // Try common title view IDs first
            for (titleId in mutableListOf<String?>(
                "com.android.settings:id/action_bar_title",
                "android:id/title",
                "com.miui.securitycenter:id/title"
            )) {
                val nodes = root.findAccessibilityNodeInfosByViewId(titleId!!)
                if (nodes != null && !nodes.isEmpty()) {
                    val text = nodes[0].text
                    nodes.forEach { it.recycle() } // CRITICAL
                    if (text != null) return text.toString()
                }
            }
            // Fallback: dump all text from the page
            if (root.text != null) return root.text.toString()
        } catch (ignored: Exception) {
        }
        return ""
    }

    // ── Private helpers ───────────────────────────────────────────────────────
    private fun hasAnyText(root: AccessibilityNodeInfo?, candidates: MutableSet<String?>): Boolean {
        for (candidate in candidates) {
            if (hasExactText(root, candidate)) return true
        }
        return false
    }

    private fun hasExactText(root: AccessibilityNodeInfo?, text: String?): Boolean {
        try {
            if (root == null || text == null) return false
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (nodes != null && !nodes.isEmpty()) {
                nodes.forEach { it.recycle() } // CRITICAL
                return true
            }
        } catch (e: Exception) {
        }
        return false
    }
}