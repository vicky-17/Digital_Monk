package com.example.digitalmonk.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.digitalmonk.core.base.BaseActivity
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.ui.onboarding.PermissionSetupActivity
import com.example.digitalmonk.ui.theme.DigitalMonkTheme

/**
 * PinSetupActivity remains in Kotlin to host the Compose UI.
 * It acts as a bridge to your Java logic layer.
 */
class PinSetupActivity : BaseActivity() {

    // Using your converted Java PrefsManager
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PrefsManager(this)

        setContent {
            DigitalMonkTheme {
                PinSetupScreen(
                    onPinSaved = {
                        // Mark setup as complete in the Java Prefs layer
                        // Note: Depending on your flow, you might do this here or inside PermissionSetupActivity
                        // prefs.setSetupComplete(true)

                        startActivity(Intent(this, PermissionSetupActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}