package com.example.digitalmonk.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.digitalmonk.ui.dashboard.MainActivity
import com.example.digitalmonk.core.base.BaseActivity
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.ui.auth.PinSetupActivity
import com.example.digitalmonk.ui.theme.DigitalMonkTheme

/**
 * OnboardingActivity remains in Kotlin.
 * It acts as the entry point for new users, checking setup status via Java PrefsManager.
 */
class OnboardingActivity : BaseActivity() {

    // Accessing your Java-based Preferences
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PrefsManager(this)

        // 1. Check if setup is already complete in the Java layer
        if (prefs.isSetupComplete) {
            navigateToDashboard()
            return
        }

        // 2. Otherwise, show the Onboarding UI
        setContent {
            DigitalMonkTheme {
                OnboardingScreen(
                    onGetStarted = {
                        startActivity(Intent(this, PinSetupActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}