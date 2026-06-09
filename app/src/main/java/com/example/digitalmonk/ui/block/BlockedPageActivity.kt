package com.example.digitalmonk.ui.block

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.digitalmonk.core.utils.PermissionHelper
import com.example.digitalmonk.core.utils.PersistenceManager
import com.example.digitalmonk.ui.theme.DigitalMonkTheme

class BlockedPageActivity : ComponentActivity() {

    companion object {

        // ── Extra keys ────────────────────────────────────────────────────────
        private const val EXTRA_EMOJI           = "gate_emoji"
        private const val EXTRA_TITLE           = "gate_title"
        private const val EXTRA_MESSAGE         = "gate_message"
        private const val EXTRA_SEVERITY        = "gate_severity"
        private const val EXTRA_STEPS           = "gate_steps"
        private const val EXTRA_SETTINGS_ACTION = "gate_settings_action"
        private const val EXTRA_FIX_LABEL       = "gate_fix_label"

        // ── Settings action constants ─────────────────────────────────────────
        const val ACTION_OPEN_ACCESSIBILITY     = "open_accessibility"
        const val ACTION_OPEN_OVERLAY           = "open_overlay"
        const val ACTION_OPEN_USAGE_STATS       = "open_usage_stats"
        const val ACTION_OPEN_BATTERY           = "open_battery"
        const val ACTION_OPEN_VPN_SETTINGS      = "open_vpn_settings"
        const val ACTION_NONE                   = "none"

        // ── Private base builder ──────────────────────────────────────────────
        private fun base(context: Context): Intent =
            Intent(context, BlockedPageActivity::class.java).apply {
                flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_NO_ANIMATION
            }

        // ── Presets ───────────────────────────────────────────────────────────

        fun settingsBlock(context: Context): Intent =
            base(context)
                .putExtra(EXTRA_EMOJI,    "🚫")
                .putExtra(EXTRA_TITLE,    "Page Blocked")
                .putExtra(EXTRA_MESSAGE,  "This settings page is restricted.")
                .putExtra(EXTRA_SEVERITY, "CRITICAL")
                .putExtra(EXTRA_SETTINGS_ACTION, ACTION_NONE)

        fun anotherVpnActive(context: Context): Intent =
            base(context)
                .putExtra(EXTRA_EMOJI,    "⚠️")
                .putExtra(EXTRA_TITLE,    "VPN Filter Bypassed")
                .putExtra(EXTRA_MESSAGE,  "Another VPN is active. Digital Monk's content filter is not running.")
                .putExtra(EXTRA_SEVERITY, "CRITICAL")
                .putExtra(EXTRA_STEPS, arrayListOf(
                    "Disconnect the other VPN app",
                    "Return to Digital Monk",
                    "Re-enable SafeSearch & Web Filter"
                ))
                .putExtra(EXTRA_SETTINGS_ACTION, ACTION_OPEN_VPN_SETTINGS)
                .putExtra(EXTRA_FIX_LABEL, "Open VPN Settings")

        fun vpnPermissionRevoked(context: Context): Intent =
            base(context)
                .putExtra(EXTRA_EMOJI,    "🔒")
                .putExtra(EXTRA_TITLE,    "VPN Permission Revoked")
                .putExtra(EXTRA_MESSAGE,  "Digital Monk no longer has VPN permission. Ask a parent to re-grant it.")
                .putExtra(EXTRA_SEVERITY, "CRITICAL")
                .putExtra(EXTRA_STEPS, arrayListOf(
                    "Open Digital Monk",
                    "Go to Permissions screen",
                    "Re-grant VPN permission"
                ))
                .putExtra(EXTRA_SETTINGS_ACTION, ACTION_OPEN_VPN_SETTINGS)
                .putExtra(EXTRA_FIX_LABEL, "Open VPN Settings")

        fun accessibilityDisabled(context: Context): Intent =
            base(context)
                .putExtra(EXTRA_EMOJI,    "🔒")
                .putExtra(EXTRA_TITLE,    "Accessibility Disabled")
                .putExtra(EXTRA_MESSAGE,  "App blocking and Shorts filtering are not active.")
                .putExtra(EXTRA_SEVERITY, "WARNING")
                .putExtra(EXTRA_STEPS, arrayListOf(
                    "Tap 'Fix Permission' below",
                    "Find Digital Monk in the list",
                    "Enable the toggle",
                    "Return here — protection activates instantly"
                ))
                .putExtra(EXTRA_SETTINGS_ACTION, ACTION_OPEN_ACCESSIBILITY)
                .putExtra(EXTRA_FIX_LABEL, "Fix Accessibility")

        fun overlayPermissionMissing(context: Context): Intent =
            base(context)
                .putExtra(EXTRA_EMOJI,    "⚠️")
                .putExtra(EXTRA_TITLE,    "Overlay Permission Missing")
                .putExtra(EXTRA_MESSAGE,  "Block screens cannot be shown without Display-over-other-apps permission.")
                .putExtra(EXTRA_SEVERITY, "WARNING")
                .putExtra(EXTRA_STEPS, arrayListOf(
                    "Tap 'Fix Permission' below",
                    "Find Digital Monk",
                    "Enable 'Allow display over other apps'"
                ))
                .putExtra(EXTRA_SETTINGS_ACTION, ACTION_OPEN_OVERLAY)
                .putExtra(EXTRA_FIX_LABEL, "Fix Overlay Permission")

        fun usageStatsMissing(context: Context): Intent =
            base(context)
                .putExtra(EXTRA_EMOJI,    "⚠️")
                .putExtra(EXTRA_TITLE,    "Usage Access Missing")
                .putExtra(EXTRA_MESSAGE,  "Screen time tracking has stopped. Usage access permission is required.")
                .putExtra(EXTRA_SEVERITY, "WARNING")
                .putExtra(EXTRA_STEPS, arrayListOf(
                    "Tap 'Fix Permission' below",
                    "Find Digital Monk",
                    "Enable Usage Access"
                ))
                .putExtra(EXTRA_SETTINGS_ACTION, ACTION_OPEN_USAGE_STATS)
                .putExtra(EXTRA_FIX_LABEL, "Fix Usage Access")

        fun batteryOptimizationActive(context: Context): Intent =
            base(context)
                .putExtra(EXTRA_EMOJI,    "🔋")
                .putExtra(EXTRA_TITLE,    "Battery Optimization Active")
                .putExtra(EXTRA_MESSAGE,  "Android may kill protection services at any time.")
                .putExtra(EXTRA_SEVERITY, "WARNING")
                .putExtra(EXTRA_STEPS, arrayListOf(
                    "Tap 'Fix Permission' below",
                    "Select 'Don't optimize' or 'Unrestricted'",
                    "Return here"
                ))
                .putExtra(EXTRA_SETTINGS_ACTION, ACTION_OPEN_BATTERY)
                .putExtra(EXTRA_FIX_LABEL, "Fix Battery Setting")
    }

    private var intentionalExit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onBackPressedDispatcher.addCallback(this) { /* swallow */ }

        // ── Read from Intent ──────────────────────────────────────────────────
        val emoji           = intent.getStringExtra(EXTRA_EMOJI)           ?: "🚫"
        val title           = intent.getStringExtra(EXTRA_TITLE)           ?: "Access Blocked"
        val message         = intent.getStringExtra(EXTRA_MESSAGE)         ?: "This page is restricted."
        val settingsAction  = intent.getStringExtra(EXTRA_SETTINGS_ACTION) ?: ACTION_NONE
        val fixLabel        = intent.getStringExtra(EXTRA_FIX_LABEL)       ?: "Fix Permission"
        val severity = when (intent.getStringExtra(EXTRA_SEVERITY)) {
            "CRITICAL" -> GateSeverity.CRITICAL
            "INFO"     -> GateSeverity.INFO
            else       -> GateSeverity.WARNING
        }
        val steps = intent.getStringArrayListExtra(EXTRA_STEPS) ?: emptyList<String>()

        // ── Build actions list ────────────────────────────────────────────────
        // Primary = "Fix Permission" (opens exact settings page), only if action exists
        // Secondary = "Go to Home Screen"
        val actions = buildList {
            if (settingsAction != ACTION_NONE) {
                add(GateAction(fixLabel, isPrimary = true) {
                    intentionalExit = true
                    openSettingsPage(settingsAction)
                    // Don't finish — user will come back via back stack
                    // The watchdog will detect the fix and stop reshowing
                })
            }
            add(GateAction("Go to Home Screen", isPrimary = settingsAction == ACTION_NONE) {
                intentionalExit = true
                startActivity(
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
                finish()
            })
        }

        setContent {
            DigitalMonkTheme {
                ProtectionGateScreen(
                    emoji    = emoji,
                    title    = title,
                    message  = message,
                    severity = severity,
                    steps    = steps,
                    actions  = actions
                )
            }
        }
    }

    /**
     * Routes to the exact system settings page for the given action.
     * Uses PermissionHelper which already has all the correct intents.
     */
    private fun openSettingsPage(action: String) {
        when (action) {
            ACTION_OPEN_ACCESSIBILITY -> PermissionHelper.openAccessibilityServiceScreen(this)
            ACTION_OPEN_OVERLAY       -> PermissionHelper.openOverlaySettings(this)
            ACTION_OPEN_USAGE_STATS   -> PermissionHelper.openUsageAccessSettings(this)
            ACTION_OPEN_BATTERY       -> PermissionHelper.openBatteryOptimizationSettings(this)
            ACTION_OPEN_VPN_SETTINGS  -> PermissionHelper.openVpnSettings(this)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = true
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = true
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean = true

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!intentionalExit) {
            startActivity(
                Intent(this, BlockedPageActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
        }
        intentionalExit = false
    }
}