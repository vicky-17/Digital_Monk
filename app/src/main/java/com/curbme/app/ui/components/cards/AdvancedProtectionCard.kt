package com.curbme.app.ui.components.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AdvancedProtectionCard(
    isShizukuInstalled: Boolean,
    isShizukuAvailable: Boolean,
    hasShizukuPermission: Boolean,
    isSecureSettingsGranted: Boolean,
    isDeviceOwner: Boolean,
    isAutoHealEnabled: Boolean,
    onRequestShizukuPermission: () -> Unit,
    onGrantSecureSettings: () -> Unit,
    onPromoteDeviceOwner: () -> Unit,
    onOpenShizukuGuide: () -> Unit,
    onReinforceBackground: () -> Unit,
    onToggleAutoHeal: (Boolean) -> Unit
) {
    var expandedShizuku by remember { mutableStateOf(false) }
    var expandedSecureSettings by remember { mutableStateOf(false) }
    var expandedDeviceOwner by remember { mutableStateOf(false) }

    // Protection tier calculation
    val currentTier = when {
        isDeviceOwner -> "Level 3: Ultimate Device Owner" to Color(0xFF22C55E)
        hasShizukuPermission -> "Level 2: Shizuku Enhanced" to Color(0xFF38BDF8)
        else -> "Level 1: Standard Protection" to Color(0xFF94A3B8)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Tier Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(currentTier.second)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ADVANCED SYSTEM HARDENING",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = currentTier.second.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = currentTier.first,
                        color = currentTier.second,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Expandable Card 1: Shizuku Engine & Auto-Healing
            val shizukuSubtitle = when {
                hasShizukuPermission -> "Connected & Auto-Healing Active ✅"
                isShizukuAvailable -> "Running - Permission Needed ⚠️"
                isShizukuInstalled -> "Installed - Service Stopped 🛑"
                else -> "Not Installed"
            }

            ProtectionSubCard(
                title = "Shizuku Engine & Auto-Healing",
                subtitle = shizukuSubtitle,
                iconColor = if (hasShizukuPermission) Color(0xFF38BDF8) else Color(0xFFF59E0B),
                isExpanded = expandedShizuku,
                onToggleExpand = { expandedShizuku = !expandedShizuku }
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Shizuku allows CurbMe to auto-heal Accessibility Service if killed by OEM battery savers and whitelist CurbMe from Doze mode.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!hasShizukuPermission) {
                            if (!isShizukuAvailable) {
                                Button(
                                    onClick = onOpenShizukuGuide,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isShizukuInstalled) "Start Shizuku Service Guide" else "Setup / Install Shizuku Guide", fontSize = 12.sp)
                                }
                            } else {
                                Button(
                                    onClick = onRequestShizukuPermission,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Grant Shizuku Permission", fontSize = 12.sp)
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = onReinforceBackground,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Reinforce Background Whitelist", fontSize = 12.sp)
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Auto-Repair Permissions",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Auto-reconnects Accessibility Service if turned off or killed by OEM battery savers.",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = isAutoHealEnabled,
                                        onCheckedChange = onToggleAutoHeal,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF38BDF8),
                                            uncheckedThumbColor = Color(0xFF94A3B8),
                                            uncheckedTrackColor = Color(0xFF334155)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expandable Card 2: WRITE_SECURE_SETTINGS
            ProtectionSubCard(
                title = "Secure Settings (WRITE_SECURE_SETTINGS)",
                subtitle = if (isSecureSettingsGranted) "Granted - Direct System Controls Active" else "Not Granted",
                iconColor = if (isSecureSettingsGranted) Color(0xFF22C55E) else Color(0xFF94A3B8),
                isExpanded = expandedSecureSettings,
                onToggleExpand = { expandedSecureSettings = !expandedSecureSettings }
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Allows CurbMe to lock Private DNS directly without a VPN and enforce Grayscale Mode during focus hours.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!isSecureSettingsGranted) {
                        Button(
                            onClick = onGrantSecureSettings,
                            enabled = hasShizukuPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF22C55E),
                                disabledContainerColor = Color(0xFF334155)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (hasShizukuPermission) "Grant Permission via Shizuku" else "Requires Shizuku Permission First",
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Text(
                            text = "✅ System write settings active",
                            color = Color(0xFF22C55E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expandable Card 3: Unbreakable Device Owner Lock
            ProtectionSubCard(
                title = "Unbreakable Device Owner Lock",
                subtitle = if (isDeviceOwner) "Device Owner Active - Unbreakable" else "Inactive",
                iconColor = if (isDeviceOwner) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                isExpanded = expandedDeviceOwner,
                onToggleExpand = { expandedDeviceOwner = !expandedDeviceOwner }
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Promotes CurbMe to Device Owner to block uninstall and force-stop entirely at the OS kernel/system level.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!isDeviceOwner) {
                        Button(
                            onClick = onPromoteDeviceOwner,
                            enabled = hasShizukuPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF59E0B),
                                disabledContainerColor = Color(0xFF334155)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (hasShizukuPermission) "Make CurbMe Device Owner (1-Tap)" else "Requires Shizuku Permission First",
                                color = if (hasShizukuPermission) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = "✅ Device Owner active - CurbMe cannot be uninstalled",
                            color = Color(0xFF22C55E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectionSubCard(
    title: String,
    subtitle: String,
    iconColor: Color,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E293B),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = subtitle,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                content()
            }
        }
    }
}
