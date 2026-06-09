package com.example.digitalmonk.ui.block

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.digitalmonk.ui.theme.DigitalMonkTheme

class BlockedPageActivity : ComponentActivity() {

    companion object {

        // ── Extra keys ────────────────────────────────────────────────────────
        private const val EXTRA_EMOJI    = "gate_emoji"
        private const val EXTRA_TITLE    = "gate_title"
        private const val EXTRA_MESSAGE  = "gate_message"
        private const val EXTRA_SEVERITY = "gate_severity"  // "CRITICAL"|"WARNING"|"INFO"
        private const val EXTRA_STEPS    = "gate_steps"     // ArrayList<String>

        // ── Private base builder ──────────────────────────────────────────────
        private fun base(context: Context): Intent =
            Intent(context, BlockedPageActivity::class.java).apply {
                flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_NO_ANIMATION
            }

        // ── Presets — one per use-case ────────────────────────────────────────

        /** SettingsPageReader — dangerous settings page detected */
        fun settingsBlock(context: Context): Intent =
            base(context)
                .putExtra(EXTRA_EMOJI,    "🚫")
                .putExtra(EXTRA_TITLE,    "Page Blocked")
                .putExtra(EXTRA_MESSAGE,  "This settings page is restricted.")
                .putExtra(EXTRA_SEVERITY, "CRITICAL")

        /** Foreign VPN is overriding Digital Monk's filter */
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

        /** VPN permission was revoked */
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

        /** Accessibility service was turned off */
        fun accessibilityDisabled(context: Context): Intent =
            base(context)
                .putExtra(EXTRA_EMOJI,    "🔒")
                .putExtra(EXTRA_TITLE,    "Accessibility Disabled")
                .putExtra(EXTRA_MESSAGE,  "App blocking and Shorts filtering are not active.")
                .putExtra(EXTRA_SEVERITY, "WARNING")
                .putExtra(EXTRA_STEPS, arrayListOf(
                    "Tap 'Go to Home Screen'",
                    "Open Digital Monk",
                    "Go to Permissions and re-enable Accessibility"
                ))

        /** Overlay permission missing */
        fun overlayPermissionMissing(context: Context): Intent =
            base(context)
                .putExtra(EXTRA_EMOJI,    "⚠️")
                .putExtra(EXTRA_TITLE,    "Overlay Permission Missing")
                .putExtra(EXTRA_MESSAGE,  "Block screens cannot be shown without Display-over-other-apps permission.")
                .putExtra(EXTRA_SEVERITY, "WARNING")

        /** Usage stats permission missing */
        fun usageStatsMissing(context: Context): Intent =
            base(context)
                .putExtra(EXTRA_EMOJI,    "⚠️")
                .putExtra(EXTRA_TITLE,    "Usage Access Missing")
                .putExtra(EXTRA_MESSAGE,  "Screen time tracking has stopped. Usage access permission is required.")
                .putExtra(EXTRA_SEVERITY, "WARNING")

        /** Battery optimization is active */
        fun batteryOptimizationActive(context: Context): Intent =
            base(context)
                .putExtra(EXTRA_EMOJI,    "🔋")
                .putExtra(EXTRA_TITLE,    "Battery Optimization Active")
                .putExtra(EXTRA_MESSAGE,  "Android may kill protection services at any time. Disable battery optimization for Digital Monk.")
                .putExtra(EXTRA_SEVERITY, "WARNING")
    }

    private var intentionalExit = false

    @RequiresApi(Build.VERSION_CODES.O_MR1)
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

        // ── Read display data from Intent ─────────────────────────────────────
        val emoji    = intent.getStringExtra(EXTRA_EMOJI)    ?: "🚫"
        val title    = intent.getStringExtra(EXTRA_TITLE)    ?: "Access Blocked"
        val message  = intent.getStringExtra(EXTRA_MESSAGE)  ?: "This page is restricted."
        val severity = when (intent.getStringExtra(EXTRA_SEVERITY)) {
            "CRITICAL" -> GateSeverity.CRITICAL
            "INFO"     -> GateSeverity.INFO
            else       -> GateSeverity.WARNING
        }
        val steps = intent.getStringArrayListExtra(EXTRA_STEPS) ?: emptyList<String>()

        setContent {
            DigitalMonkTheme {
                ProtectionGateScreen(
                    emoji    = emoji,
                    title    = title,
                    message  = message,
                    severity = severity,
                    steps    = steps,
                    actions  = listOf(
                        GateAction("Go to Home Screen") {
                            intentionalExit = true
                            startActivity(
                                Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_HOME)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            )
                            finish()
                        }
                    )
                )
            }
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