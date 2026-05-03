package com.example.digitalmonk.ui.block

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.example.digitalmonk.core.base.BaseActivity
import com.example.digitalmonk.ui.theme.DigitalMonkTheme

class BlockedPageActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}