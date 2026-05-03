package com.example.digitalmonk.ui.overlay

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.digitalmonk.ui.theme.inknutAntiqua

enum class OverlayState { BOTTOM, SHRUNK, FULL }

@Composable
fun UnifiedOverlay(state: OverlayState, onGoHome: () -> Unit) {
    // This Surface will automatically slide its height when content changes
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        color = Color(0xF0080E1A), // Dark slate
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                OverlayState.BOTTOM -> {
                    Text("🛡️ Digital Monk Active", color = Color.White, fontFamily = inknutAntiqua)
                }
                OverlayState.SHRUNK -> {
                    Text("Settings restricted", color = Color.Gray, fontSize = 12.sp)
                }
                OverlayState.FULL -> {
                    Text("Access Denied", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Enter Parent PIN to modify settings.", color = Color.White)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onGoHome) { Text("Go Home") }
                }
            }
        }
    }
}