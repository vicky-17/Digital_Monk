package com.example.digitalmonk.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.digitalmonk.core.base.BaseActivity
import com.example.digitalmonk.service.WatchdogService
import com.example.digitalmonk.ui.MainActivity
import com.example.digitalmonk.ui.theme.DigitalMonkTheme

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