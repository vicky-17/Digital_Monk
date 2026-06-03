package com.example.digitalmonk.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.digitalmonk.ui.components.common.SectionLabel
import com.example.digitalmonk.ui.theme.DigitalMonkTheme

// Reusable local palette variables to sync with your main dashboard skin theme[cite: 1]
private val BgDeep      = Color(0xFF080E1A) // Matches MainActivity theme background[cite: 1]
private val BgCard      = Color(0xFF111827) // Standard dark panel color[cite: 1]
private val AccentBlue  = Color(0xFF3B82F6) // DigitalMonk action brand color[cite: 1]
private val TextPrimary = Color(0xFFF1F5F9) // Main bright text readable layer[cite: 1]
private val TextSecond  = Color(0xFF64748B) // Subtitle text layer[cite: 1]

@Composable
fun SettingsScreen(
    onNavigateToPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        SectionLabel("System Diagnostics")
        Spacer(modifier = Modifier.height(8.dp))

        // Reusable card component targeting your critical background configurations[cite: 1]
        SettingsNavigationCard(
            title = "App Permissions status",
            description = "Check or fix Accessibility, Device Admin, and Always-On VPN configurations.",
            icon = Icons.Rounded.Shield,
            onClick = onNavigateToPermissions
        )

        Spacer(modifier = Modifier.height(16.dp))
        // You can comfortably append theme selectors or device info profiles below later[cite: 1]
    }
}

@Composable
fun SettingsNavigationCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(AccentBlue.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    fontSize = 15.sp
                )
                Text(
                    text = description,
                    color = TextSecond,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = TextSecond,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// --- ANDROID STUDIO SPLIT-VIEW PREVIEW ---
@Preview(name = "Settings Operational Interface", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
fun SettingsScreenPreview() {
    DigitalMonkTheme {
        // We pass an empty lambda block '{}' to fulfill the callback requirement for preview layout stability[cite: 1]
        SettingsScreen(onNavigateToPermissions = {})
    }
}