package com.example.digitalmonk.ui.block

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.example.digitalmonk.core.base.BaseActivity
import com.example.digitalmonk.ui.theme.DigitalMonkTheme

class BlockedPageActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on and make it show over lock screen if needed
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        // Block hardware back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Swallow back — do nothing
            }
        })

        setContent {
            DigitalMonkTheme {
                BlockedPageScreen(
                    onGoHome = {
                        val home = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(home)
                        finish()
                    }
                )
            }
        }
    }

    // Re-launch this screen if user navigated away via Home/Recents
    override fun onResume() {
        super.onResume()
        // Already on screen — no action needed
    }

    override fun onRestart() {
        super.onRestart()
        // User came back from recents — still blocked, nothing to do
    }

    // Block all hardware key events except volume (which you can't block anyway)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_APP_SWITCH,  // Recents button
            KeyEvent.KEYCODE_HOME,         // Some devices send this
            KeyEvent.KEYCODE_MENU -> true  // Swallow
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // Intercept ALL touch events at the window level before Compose sees them
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // Let Compose handle it — the pointerInput in BlockedPageScreen
        // will consume everything except the Go Home button click
        return super.dispatchTouchEvent(ev)
    }

    override fun onUserLeaveHint() {
        // Called when Home is pressed — we can't prevent it,
        // but we can relaunch ourselves
        val relaunch = Intent(this, BlockedPageActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(relaunch)
    }
}