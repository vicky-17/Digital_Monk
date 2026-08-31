package com.curbme.app.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.curbme.app.ui.MainActivity
import com.curbme.app.core.base.BaseActivity
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.ui.auth.PinSetupActivity
import com.curbme.app.ui.theme.CurbMeTheme

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
            CurbMeTheme {
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