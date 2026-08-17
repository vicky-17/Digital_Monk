package com.digitalmonk.app.ui.components.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitalmonk.app.ui.sidebar.formatRemainingTime
import androidx.compose.material3.SwitchColors
import androidx.compose.ui.draw.scale
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun DnsProtectionCard(
    isEnabled: Boolean,
    selectedHostname: String,
    isSettingsLocked: Boolean,      // The Android system restriction
    isTimedLockActive: Boolean,     // Digital Monk's internal lock period
    lockUntil: Long,
    isDeviceOwner: Boolean,
    onDnsToggle: (Boolean) -> Unit,
    onHostClick: () -> Unit,
    onSettingsLockToggle: (Boolean) -> Unit,
    onLockClick: () -> Unit
) {
    val cardBg = Color(0xFF0F172A)
    val footerBg = Color(0xAD002541) // Darker for the status bar look
    val accentCyan = Color(0xFF06B6D4)
    val gold = Color(0xFF4CAF50)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // --- 1. Master DNS Toggle ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) accentCyan.copy(0.1f) else Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🌐", fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Private DNS", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("System-wide web filtering", fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onDnsToggle,
                    enabled = !isTimedLockActive, // Disable if Monk Lock is on
                    colors = SwitchDefaults.colors(checkedTrackColor = accentCyan)
                )
            }

            // --- 2. Sub-Settings (Visible only if enabled) ---
            AnimatedVisibility(visible = isEnabled) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                    Spacer(Modifier.height(12.dp))

                    // Hostname Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isTimedLockActive) { onHostClick() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DNS Provider", modifier = Modifier.weight(1f), fontSize = 13.sp, color = Color(0xFF94A3B8))
                        Text(selectedHostname, fontSize = 13.sp, color = if(isTimedLockActive) Color.Gray else accentCyan)
                        Icon(Icons.Rounded.ChevronRight, "", tint = Color(0xFF475569), modifier = Modifier.size(18.dp))
                    }

                    // Settings Shield Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Settings Shield", fontSize = 13.sp, color = Color.White)
                            Text("Block Android Settings bypass", fontSize = 10.sp, color = Color(0xFF64748B))
                        }
                        Switch(
                            checked = isSettingsLocked,
                            onCheckedChange = onSettingsLockToggle,
                            // Strict Logic: Can turn ON even if locked, but not OFF.
                            enabled = !isTimedLockActive || !isSettingsLocked,
                            scale = 0.8f, // Slightly smaller sub-switch
                            colors = SwitchDefaults.colors(checkedTrackColor = accentCyan)
                        )
                    }
                }
            }

            // --- 3. The "Monk Lock" Status Bar (The footer) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(footerBg)
                    .clickable(enabled = !isTimedLockActive) { onLockClick() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = if (isTimedLockActive) gold else Color(0xFFFF2222),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isTimedLockActive) {
                        val remaining = lockUntil - System.currentTimeMillis()
                        Text(
                            "Locked until ${formatRemainingTime(remaining)}",
                            color = gold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            "Set Protection Lock",
                            color = Color(0xFFFF5722),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// 1. Updated Helper Switch with scale support
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scale: Float = 1.0f, // Added back the scale parameter
    colors: SwitchColors = SwitchDefaults.colors()
) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.scale(scale), // Apply scale here
        enabled = enabled,
        colors = colors
    )
}

// 2. Previews
@Preview(name = "DNS Card — Unlocked", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
fun DnsProtectionCardPreviewUnlocked() {
    Box(Modifier.padding(16.dp)) {
        DnsProtectionCard(
            isEnabled = true,
            selectedHostname = "dns.adguard.com",
            isSettingsLocked = false,
            isTimedLockActive = false,
            lockUntil = 0L,
            isDeviceOwner = true,
            onDnsToggle = {},
            onHostClick = {},
            onSettingsLockToggle = {},
            onLockClick = {}
        )
    }
}

@Preview(name = "DNS Card — Locked", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
fun DnsProtectionCardPreviewLocked() {
    Box(Modifier.padding(16.dp)) {
        DnsProtectionCard(
            isEnabled = true,
            selectedHostname = "family.cloudflare-dns.com",
            isSettingsLocked = true,
            isTimedLockActive = true,
            lockUntil = System.currentTimeMillis() + 86400000L, // 24 hours
            isDeviceOwner = true,
            onDnsToggle = {},
            onHostClick = {},
            onSettingsLockToggle = {},
            onLockClick = {}
        )
    }
}