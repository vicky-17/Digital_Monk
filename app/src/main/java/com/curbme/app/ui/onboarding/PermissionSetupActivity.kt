package com.curbme.app.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.curbme.app.core.base.BaseActivity
import com.curbme.app.service.WatchdogService
import com.curbme.app.ui.MainActivity
import com.curbme.app.ui.theme.CurbMeTheme

class PermissionSetupActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CurbMeTheme {
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