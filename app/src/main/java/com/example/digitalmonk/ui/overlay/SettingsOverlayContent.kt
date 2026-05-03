package com.example.digitalmonk.ui.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SettingsOverlayStage {
    STRIP,   // 80dp bar — safe page / exploring
    HALF,    // 650dp — initial block on settings open
    FULL     // full screen — dangerous uninstallation page
}

@Composable
fun SettingsBlockOverlay(
    stage: SettingsOverlayStage,
    onGoHome: () -> Unit = {}
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight

        val targetHeight: Dp = when (stage) {
            SettingsOverlayStage.STRIP -> 80.dp
            SettingsOverlayStage.HALF  -> 650.dp.coerceAtMost(screenHeight * 0.8f)
            SettingsOverlayStage.FULL  -> screenHeight
        }

        val animatedHeight by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = tween(durationMillis = 350),
            label = "overlayHeight"
        )

        val animatedCorner by animateDpAsState(
            targetValue = if (stage == SettingsOverlayStage.FULL) 0.dp else 20.dp,
            animationSpec = tween(durationMillis = 400),
            label = "cornerRadius"
        )

        val stripAlpha by animateFloatAsState(
            targetValue = if (stage == SettingsOverlayStage.STRIP) 1f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "stripAlpha"
        )

        val richAlpha by animateFloatAsState(
            targetValue = if (stage == SettingsOverlayStage.STRIP) 0f else 1f,
            animationSpec = tween(durationMillis = 200),
            label = "richAlpha"
        )

        val fullButtonAlpha by animateFloatAsState(
            targetValue = if (stage == SettingsOverlayStage.FULL) 1f else 0f,
            animationSpec = tween(durationMillis = 300),
            label = "fullButtonAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedHeight)
                .align(Alignment.BottomCenter)
                .clip(
                    RoundedCornerShape(
                        topStart = animatedCorner,
                        topEnd = animatedCorner,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp
                    )
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF080E1A), Color(0xFF0D1520))
                    )
                )
        ) {
            // ── STRIP row ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .alpha(stripAlpha)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🛡️  Protected by Digital Monk",
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            // ── HALF / FULL rich content ───────────────────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .alpha(richAlpha)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "🛡️", fontSize = 52.sp)

                Text(
                    text = "Protected by Digital Monk",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "This page is restricted.\nA parent PIN is required to make changes here.",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onGoHome,
                    modifier = Modifier
                        .alpha(fullButtonAlpha)
                        .fillMaxWidth(0.75f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "← Go to Home Screen",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}