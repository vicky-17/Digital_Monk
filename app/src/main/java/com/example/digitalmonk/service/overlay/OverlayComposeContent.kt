package com.example.digitalmonk.service.overlay


import androidx.compose.runtime.Composable

enum class OverlayState {
    BOTTOM,    // Frame 1 — settings just opened, large bottom blocker
    SHRUNK,    // Frame 2 — safe page detected, small banner
    FULL       // Frame 3 — dangerous page confirmed, full screen
}

@Composable
fun OverlayComposeContent(
    state: OverlayState,
    onGoHomeClick: () -> Unit
) {
    when (state) {
        OverlayState.BOTTOM  -> SplashScreen()
        OverlayState.SHRUNK  -> ProtectionBanner()
        OverlayState.FULL    -> BlockedPageScreen(onGoToHomeClick = onGoHomeClick)
    }
}