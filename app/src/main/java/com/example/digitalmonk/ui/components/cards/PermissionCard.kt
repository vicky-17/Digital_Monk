package com.example.digitalmonk.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Reusable Theme Colors (Ideally these should be in your Color.kt)
private val AccentGreen = Color(0xFF10B981)
private val AccentRed = Color(0xFFEF4444)
private val AccentAmber = Color(0xFFF59E0B)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecond = Color(0xFF64748B)

@Composable
fun PermissionCard(
    emoji: String,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    isCritical: Boolean = false,
    onAction: () -> Unit
) {
    // 1. Logic for dynamic coloring based on state
    val bgColor = if (isGranted) Color(0xFF0A1F14) else Color.Transparent
    val statusColor = when {
        isGranted -> AccentGreen
        isCritical -> AccentRed
        else -> AccentAmber
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            // 2. Only allow clicking if the permission isn't granted yet
            .clickable(enabled = !isGranted, onClick = onAction)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Emoji Icon Container
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 18.sp)
        }

        Spacer(Modifier.width(12.dp))

        // Text Content
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = TextSecond)
        }

        Spacer(Modifier.width(8.dp))

        // Status Badge
        if (isGranted) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AccentGreen.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", fontSize = 12.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(statusColor.copy(0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Fix →", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor)
            }
        }
    }
}



@Preview(showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
fun PermissionCardPreview() {
    Column(modifier = Modifier.padding(16.dp)) {
        // State 1: Not Granted & Critical
        Text("Critical - Pending", color = Color.White, fontSize = 12.sp)
        PermissionCard(
            emoji = "♿",
            title = "Accessibility Service",
            subtitle = "Required for app & Shorts blocking",
            isGranted = false,
            isCritical = true,
            onAction = {}
        )

        Spacer(Modifier.height(16.dp))

        // State 2: Not Granted & Normal
        Text("Important - Pending", color = Color.White, fontSize = 12.sp)
        PermissionCard(
            emoji = "🛡️",
            title = "Device Admin",
            subtitle = "Prevents app from being uninstalled",
            isGranted = false,
            isCritical = false,
            onAction = {}
        )

        Spacer(Modifier.height(16.dp))

        // State 3: Granted
        Text("Granted State", color = Color.White, fontSize = 12.sp)
        PermissionCard(
            emoji = "🔋",
            title = "Battery Optimization",
            subtitle = "Keeps app alive in background",
            isGranted = true,
            isCritical = true,
            onAction = {}
        )
    }
}







