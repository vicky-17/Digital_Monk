package com.example.digitalmonk.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isEnabled) Color(0xFF0A1520) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
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

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = TextSecond, lineHeight = 15.sp)
        }

        Spacer(Modifier.width(8.dp))

        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentCyan,
                uncheckedThumbColor = Color(0xFF64748B),
                uncheckedTrackColor = TextMuted
            )
        )
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
            onToggle = {}
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