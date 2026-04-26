package com.example.digitalmonk.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.digitalmonk.core.base.BaseActivity
import com.example.digitalmonk.ui.onboarding.PermissionSetupActivity
import com.example.digitalmonk.ui.theme.DigitalMonkTheme

class PinSetupActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DigitalMonkTheme {
                PinSetupScreen(
                    onPinSaved = {
                        startActivity(Intent(this, PermissionSetupActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}