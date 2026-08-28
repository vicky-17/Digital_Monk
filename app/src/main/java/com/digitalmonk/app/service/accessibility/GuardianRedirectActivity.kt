package com.digitalmonk.app.service.accessibility

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.digitalmonk.app.service.overlay.SettingsBlockOverlayService
import com.digitalmonk.app.ui.MainActivity

/**
 * GuardianRedirectActivity
 * 
 * Launched by SettingsPageReader when the "Device admin app" or "App Info"
 * page is detected. Immediately covers the dangerous Settings page by
 * launching our own MainActivity on top, then finishes itself.
 * 
 * No SYSTEM_ALERT_WINDOW permission needed — Activity launch is sufficient.
 */
class GuardianRedirectActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Black screen — child sees nothing between Settings and our app
        getWindow().getDecorView().setBackgroundColor(Color.BLACK)
        if (getActionBar() != null) getActionBar()!!.hide()

        Log.w(TAG, "GuardianRedirectActivity launched")

        redirectToMainApp()
    }

    /**
     * Immediately launches MainActivity on top of the dangerous Settings page,
     * then hides the overlay and finishes this bridge activity after a short delay.
     */
    private fun redirectToMainApp() {
        // Step 1 (0ms): Launch MainActivity — this instantly covers the Settings page.
        // FLAG_ACTIVITY_CLEAR_TOP ensures any existing MainActivity instance is reused
        // and brought to front rather than creating a duplicate.
        val intent = Intent(getApplicationContext(), MainActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        getApplicationContext().startActivity(intent)

        // Step 2 (0ms): Hide the settings block overlay since we've taken over the screen
        SettingsBlockOverlayService.hide(getApplicationContext())

        Log.i(TAG, "✅ MainActivity launched over Settings page")

        // Step 3 (500ms): Finish this transparent bridge activity.
        // Delayed slightly to ensure MainActivity has fully rendered before we disappear.
        handler.postDelayed(Runnable {
            Log.i(TAG, "Bridge activity finishing")
            finish()
        }, DELAY_FINISH_MS)
    }

    /**
     * Fallback: if something goes wrong, at minimum go to home screen.
     * Called if the accessibility service is unexpectedly null.
     */
    private fun fallbackAndFinish() {
        Log.e(TAG, "Fallback triggered — going to home screen")

        val home = Intent(Intent.ACTION_MAIN)
        home.addCategory(Intent.CATEGORY_HOME)
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(home)

        SettingsBlockOverlayService.hide(getApplicationContext())
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val TAG = "GuardianRedirect"

        // How long to wait before finishing this transparent bridge activity
        private const val DELAY_FINISH_MS = 500L
    }
}