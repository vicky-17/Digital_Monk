package com.digitalmonk.app.ui.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.digitalmonk.app.ui.theme.DigitalMonkTheme

object OverlayBridge {
    // Kotlin-owned state — Java service writes to this via the setter below
    val overlayStage = mutableStateOf(SettingsOverlayStage.HALF)

    @JvmStatic
    fun setStage(stage: SettingsOverlayStage) {
        overlayStage.value = stage
    }

    @JvmStatic
    fun setContent(composeView: ComposeView, onGoHome: Runnable) {
        composeView.setContent {
            DigitalMonkTheme {
                val stage by overlayStage
                SettingsBlockOverlay(stage = stage, onGoHome = { onGoHome.run() })
            }
        }
    }
}