package com.example.digitalmonk.ui.block

import android.content.Intent
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

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Show over lock screen
        setShowWhenLocked(true)

        // True immersive — hide status bar + nav bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Block back
        onBackPressedDispatcher.addCallback(this) {
            // Swallow
        }

        val appName = intent.getStringExtra("app_name") ?: "This"

        setContent {
            DigitalMonkTheme {
                ProtectionGateScreen(
                    emoji    = "🚫",
                    title    = "$appName is Blocked",
                    message  = "This page is restricted.",
                    severity = GateSeverity.CRITICAL,
                    actions  = listOf(
                        GateAction("Go to Home Screen") {
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

    // Re-hide bars if system temporarily shows them via swipe
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // Block all hardware keys
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = true
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = true
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean = true

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        startActivity(
            Intent(this, BlockedPageActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
    }
}