package com.example.digitalmonk.ui.overlay

import androidx.compose.ui.platform.ComposeView
import com.example.digitalmonk.ui.theme.DigitalMonkTheme

object OverlayBridge {
    @JvmStatic
    fun setContent(composeView: ComposeView, state: OverlayState, onHome: () -> Unit) {
        composeView.setContent {
            DigitalMonkTheme {
                UnifiedOverlay(state = state, onGoHome = onHome)
            }
        }
    }
}