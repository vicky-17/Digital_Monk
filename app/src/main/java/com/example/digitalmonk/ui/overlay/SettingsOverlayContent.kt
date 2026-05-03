package com.example.digitalmonk.ui.overlay

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class SettingsOverlayStage {
    HIDE, STRIP, HALF, FULL
}

@Composable
fun SettingsBlockOverlay(
    stage: SettingsOverlayStage,
    onGoHome: () -> Unit = {},
    // NEW: Callback to send the red box's coordinates back to Java
    onBoundsUpdate: (android.graphics.Rect) -> Unit = {}
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val targetHeight: Dp = when (stage) {
        SettingsOverlayStage.HIDE -> 0.dp
        SettingsOverlayStage.STRIP -> 70.dp
        SettingsOverlayStage.HALF  -> 650.dp.coerceAtMost(screenHeight * 0.8f)
        SettingsOverlayStage.FULL  -> screenHeight
    }

    // 1. Invisible Full-Screen Canvas
    Box(modifier = Modifier.fillMaxSize()) {

        // 2. The Animated Red Box
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter) // Glued to the bottom!
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(durationMillis = 300))
                .height(targetHeight)
                .background(Color.Red)
                // 3. Measure the box continuously as it animates
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    onBoundsUpdate(
                        android.graphics.Rect(
                            bounds.left.toInt(),
                            bounds.top.toInt(),
                            bounds.right.toInt(),
                            bounds.bottom.toInt()
                        )
                    )
                }
        ) {
            // Your internal text/buttons will go here later
        }
    }
}