package com.example.digitalmonk.ui.block

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.digitalmonk.ui.theme.DigitalMonkTheme

@Composable
fun BlockedPageScreen(onGoHome: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF080E1A), Color(0xFF0D1520))
                )
            )
            // Consume touches on the background box only
            // The button is drawn ON TOP so its touches are handled first
            .pointerInput(Unit) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(32.dp)
                // Stop background pointerInput from consuming button touches
                .pointerInput(Unit) {
                    awaitEachGesture {
                        while (true) {
                            awaitPointerEvent() // receive but DON'T consume
                        }
                    }
                }
        ) {
            Text("🛡️", fontSize = 64.sp)

            Text(
                "Access Blocked",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "This page is restricted by Digital Monk parental controls.",
                color = Color(0xFF94A3B8),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onGoHome,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("← Go to Home Screen", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun BlockedPageScreenPreview() {
    DigitalMonkTheme {
        BlockedPageScreen(onGoHome = {})
    }
}