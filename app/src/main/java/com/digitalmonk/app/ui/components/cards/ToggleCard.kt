package com.digitalmonk.app.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon

// Local UI Colors
private val AccentCyan   = Color(0xFF06B6D4)
private val TextPrimary  = Color(0xFFF1F5F9)
private val TextSecond   = Color(0xFF64748B)
private val TextMuted    = Color(0xFF334155)

@Composable
fun ToggleCard(
    emoji: String,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    isLocked: Boolean = false,
    onLockClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji Icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isEnabled) AccentCyan.copy(0.12f) else TextMuted.copy(0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 18.sp)
            }

            Spacer(Modifier.width(12.dp))

            // Text Column
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.sp, color = TextSecond, lineHeight = 15.sp)
            }

            // --- NEW LOCK ICON PLACEMENT ---
            if (onLockClick != null && isEnabled) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(CircleShape)
                        // If locked, show a subtle gold glow background
                        .background(if (isLocked) Color(0xFFFACC15).copy(alpha = 0.1f) else Color.Transparent)
                        .clickable(enabled = !isLocked) { onLockClick() }
                        .padding(10.dp) // Larger touch target
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Lock Settings",
                        // Yellow when locked, Grey-blue when available
                        tint = if (isLocked) Color(0xFFFFD700) else Color(0xFF94A3B8),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // Toggle Switch
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                enabled = !isLocked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentCyan,
                    uncheckedThumbColor = Color(0xFF64748B),
                    uncheckedTrackColor = TextMuted,
                    disabledCheckedTrackColor = AccentCyan.copy(alpha = 0.4f),
                    disabledCheckedThumbColor = Color.White.copy(alpha = 0.6f)
                )
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1322)
@Composable
fun ToggleCardPreview() {
    Column {
        ToggleCard(
            emoji = "♻️",
            title = "Enabled Toggle",
            subtitle = "This is what an active toggle looks like.",
            isEnabled = true,
            onToggle = {},
            isLocked = false
        )
        ToggleCard(
            emoji = "🔒",
            title = "Disabled Toggle",
            subtitle = "This is what an inactive toggle looks like.",
            isEnabled = false,
            onToggle = {}
        )
    }
}