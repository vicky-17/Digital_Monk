package com.digitalmonk.app.service.monitor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.digitalmonk.app.data.local.prefs.PrefsManager
import com.digitalmonk.app.service.accessibility.AllowlistManager
import com.digitalmonk.app.service.accessibility.GuardianAccessibilityService
import com.digitalmonk.app.service.accessibility.GuardianAccessibilityService.Companion.currentRootNode
import com.digitalmonk.app.ui.block.BlockedPageActivity.Companion.settingsBlock
import java.util.Collections
import kotlin.concurrent.Volatile

class SettingsPageReader(private val prefs: PrefsManager) {
    @Volatile
    private var escapeInProgress = true

    @Volatile
    private var lastEscapeAttemptMs = 0L

    // ── Public API ────────────────────────────────────────────────────────────
    fun readAndRespond(context: Context, settingsPkg: String?): Boolean {
        if (!prefs.isAntiUninstallEnabled) return false
        //        Log.d("MONK_TRACE", "readAndRespond() called → pkg=" + settingsPkg
//                + " | escapeInProgress=" + escapeInProgress);
//
        if (escapeInProgress) {
//            Log.d("MONK_TRACE", "readAndRespond() → SKIP: escapeInProgress");
            return false
        }

        val now = System.currentTimeMillis()
        if (now - lastEscapeAttemptMs < ESCAPE_COOLDOWN_MS) {
//            Log.d("MONK_TRACE", "readAndRespond() → SKIP: cooldown active, remaining="
//                    + (ESCAPE_COOLDOWN_MS - (now - lastEscapeAttemptMs)) + "ms");
            return false
        }

        val root = this.accessibilityRoot

        //        Log.d("MONK_TRACE", "readAndRespond() → root=" + (root != null ? "AVAILABLE" : "NULL"));
        if ("com.miui.securitycenter" == settingsPkg) {
            if (root != null && isDangerousSettingsPage(root, settingsPkg)) {
//                Log.w("MONK_TRACE", "readAndRespond() → DANGEROUS (miui path) → launching redirect");
                lastEscapeAttemptMs = now
                launchRedirectActivity(context)
                return true
            }
            //            Log.d("MONK_TRACE", "readAndRespond() → miui path, not dangerous or root null");
            return false
        }

        if (isDangerousSettingsPage(root, settingsPkg)) {
//            Log.w("MONK_TRACE", "readAndRespond() → DANGEROUS → launching redirect");
            lastEscapeAttemptMs = now
            launchRedirectActivity(context)
            return true
        }

        //        Log.d("MONK_TRACE", "readAndRespond() → safe page");
        return false
    }

    fun reset() {
        escapeInProgress = false
        //        Log.d(TAG, "SettingsPageReader reset");
    }

    // ── Launch redirect activity ──────────────────────────────────────────────
    private fun launchRedirectActivity(context: Context) {
        escapeInProgress = true
        try {
            context.startActivity(settingsBlock(context))
        } catch (e: Exception) {
            escapeInProgress = false
        }
        Handler(Looper.getMainLooper()).postDelayed(Runnable {
            escapeInProgress = false
        }, 1500L)
    }

    // ── Detection ─────────────────────────────────────────────────────────────
    private fun isDangerousSettingsPage(
        root: AccessibilityNodeInfo?,
        packageName: String?
    ): Boolean {
//        Log.d("MONK_TRACE", "isDangerousSettingsPage() → pkg=" + packageName + " | root=" + (root != null ? "ok" : "null"));

        if (packageName == null || !SETTINGS_PACKAGES.contains(packageName)) {
//            Log.d("MONK_TRACE", "isDangerousSettingsPage() → GATE1 FAIL: not a settings package");
            return false
        }
        if (root == null) {
//            Log.d("MONK_TRACE", "isDangerousSettingsPage() → GATE2 FAIL: root is null");
            return false
        }
        // ── ALLOWLIST CHECK ───────────────────────────────────────────────────
        val allowlist: AllowlistManager = AllowlistManager.getInstance()
        // Check against all visible text on the page
        for (title in DANGEROUS_TITLES) {
            if (hasExactText(root, title) && allowlist.isAnyAllowed(title)) {
                return false
            }
        }

        // Also check raw page title
        // (re-use your existing hasAnyText / hasExactText helpers)
        if (!hasAnyText(root, DANGEROUS_TITLES)) {
//            Log.d("MONK_TRACE", "isDangerousSettingsPage() → GATE3 FAIL: no dangerous title found");
            return false
        }
        if (!hasExactText(root, "Digital Monk")) {
//            Log.d("MONK_TRACE", "isDangerousSettingsPage() → GATE4 FAIL: 'Digital Monk' text not found");
            return false
        }
        // CHANGED: Gate 5 now checks body text instead of buttons
        // MIUI hides action buttons from accessibility tree — confirmed via UI dump
        if (!hasAnyText(root, DANGER_BUTTONS)) {
//            Log.d("MONK_TRACE", "isDangerousSettingsPage() → GATE5 FAIL: no confirmation text found (buttons hidden by MIUI)");
            return false
        }

        //        Log.w("MONK_TRACE", "isDangerousSettingsPage() → ALL GATES PASSED ✓");
        return true
    }

    private fun hasAnyText(root: AccessibilityNodeInfo?, candidates: MutableSet<String?>): Boolean {
        for (c in candidates) {
            if (hasExactText(root, c)) return true
        }
        return false
    }

    private fun hasExactText(root: AccessibilityNodeInfo?, text: String?): Boolean {
        try {
            if (root == null || text == null) return false
            val nodes = root.findAccessibilityNodeInfosByText(text)
            return nodes != null && !nodes.isEmpty()
        } catch (e: Exception) {
            return false
        }
    }

    private val accessibilityRoot: AccessibilityNodeInfo?
        get() {
            val connected = GuardianAccessibilityService.serviceConnectedTimestamp
            val lastEvent = GuardianAccessibilityService.lastEventTimestamp
            val now = System.currentTimeMillis()

            //        Log.d("MONK_TRACE", "getAccessibilityRoot() → connected=" + connected
//                + " | timeSinceConnected=" + (connected > 0 ? (now - connected) : "N/A")
//                + " | lastEvent=" + lastEvent
//                + " | timeSinceLastEvent=" + (lastEvent > 0 ? (now - lastEvent) : "N/A")
//                + " | graceMs=" + ACCESSIBILITY_GRACE_MS);
            if (connected == 0L) {
//            Log.d("MONK_TRACE", "getAccessibilityRoot() → NULL: service never connected");
                return null
            }
            if (now - connected < ACCESSIBILITY_GRACE_MS) {
//            Log.d("MONK_TRACE", "getAccessibilityRoot() → NULL: still in grace period");
                return null
            }
            if (lastEvent == 0L) {
//            Log.d("MONK_TRACE", "getAccessibilityRoot() → NULL: no events received yet");
                return null
            }
            if (now - lastEvent > 15000L) {
//            Log.d("MONK_TRACE", "getAccessibilityRoot() → NULL: last event too old (" + (now - lastEvent) + "ms ago)");
                return null
            }

            val root = currentRootNode
            //        Log.d("MONK_TRACE", "getAccessibilityRoot() → " + (root != null ? "GOT ROOT" : "getCurrentRootNode() returned null"));
            return root
        }

    companion object {
        private const val TAG = "SettingsPageReader"
        private const val ESCAPE_COOLDOWN_MS = 2500L
        private const val ACCESSIBILITY_GRACE_MS = 5000L

        private val SETTINGS_PACKAGES = SettingsAppMonitor.SETTINGS_PACKAGES

        private val DANGEROUS_TITLES: MutableSet<String?> = Collections.unmodifiableSet<String?>(
            HashSet<String?>(
                mutableListOf<String?>(
                    "App info",
                    "Device admin app",
                    "Application info"
                )
            )
        )

        // CHANGED: replaced button texts with body texts visible in accessibility tree
        // MIUI deliberately hides action buttons from accessibility tree as a security measure.
        // We use body text that is always rendered as TextView instead.
        private val DANGER_BUTTONS: MutableSet<String?> = Collections.unmodifiableSet<String?>(
            HashSet<String?>(
                mutableListOf<String?>( // Device Admin page — body text confirmed visible in accessibility dump
                    "This admin app is active",  // App Info page — buttons still visible on stock Android
                    "Force stop",
                    "Uninstall",
                    "Storage & cache",
                    "Storage and cache"
                )
            )
        )
    }
}