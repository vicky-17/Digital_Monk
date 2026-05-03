package com.example.digitalmonk.ui.overlay

import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import com.example.digitalmonk.service.overlay.SettingsBlockOverlayService
import com.example.digitalmonk.ui.theme.DigitalMonkTheme

object OverlayBridge {
    @JvmStatic
    fun setContent(composeView: ComposeView, onGoHome: Runnable) {
        composeView.setContent {
            DigitalMonkTheme {
                // Read the single shared state from the service
                val stage by SettingsBlockOverlayService.overlayStage
                SettingsBlockOverlay(
                    stage = stage,
                    onGoHome = { onGoHome.run() }
                )
            }
        }
    }
}