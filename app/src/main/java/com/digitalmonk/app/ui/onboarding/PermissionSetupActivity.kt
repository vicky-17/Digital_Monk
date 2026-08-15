package com.digitalmonk.app.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.digitalmonk.app.core.base.BaseActivity
import com.digitalmonk.app.service.WatchdogService
import com.digitalmonk.app.ui.MainActivity
import com.digitalmonk.app.ui.theme.DigitalMonkTheme

class PermissionSetupActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DigitalMonkTheme {
                PermissionSetupContent(
                    onComplete = {
                        WatchdogService.start(this)
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun PermissionSetupContent(onComplete: () -> Unit) {
}