package com.curbme.app.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.curbme.app.core.utils.PermissionHelper

@Composable
fun AntiUninstallPermissionDialog(
    isAccessibilityGranted: Boolean,
    isDeviceAdminGranted: Boolean,
    onDismiss: () -> Unit,
    onPermissionGrantedCheck: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = "Anti-Uninstall Security",
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Permissions Required",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Anti-Uninstall Protection",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "To prevent CurbMe from being uninstalled or force-stopped, both permissions below must be granted.",
                    fontSize = 13.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(16.dp))

                // 1. Device Admin Row
                PermissionItemRow(
                    title = "Device Administrator",
                    description = "Prevents direct uninstallation of CurbMe.",
                    isGranted = isDeviceAdminGranted,
                    onGrantClick = { PermissionHelper.openDeviceAdminSettings(context) }
                )

                Spacer(Modifier.height(12.dp))

                // 2. Accessibility Service Row
                PermissionItemRow(
                    title = "Accessibility Service",
                    description = "Prevents disabling Device Admin, Force Stop, or tampering in Settings.",
                    isGranted = isAccessibilityGranted,
                    onGrantClick = { PermissionHelper.openAccessibilityServiceScreen(context) }
                )

                Spacer(Modifier.height(16.dp))

                // Prominent Disclosure for Google Play
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF040814)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "PROMINENT DISCLOSURE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Accessibility Service is used solely to detect when CurbMe's App Info or Device Admin settings are opened, blocking unauthorized uninstallation or tampering.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        onPermissionGrantedCheck()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDeviceAdminGranted && isAccessibilityGranted) Color(0xFF22C55E) else Color(0xFF3B82F6)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isDeviceAdminGranted && isAccessibilityGranted) "Done" else "I've Granted Permissions",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItemRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFF1E293B)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF22C55E) else Color(0xFFEF4444),
                modifier = Modifier.size(22.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 14.sp
                )
            }

            if (!isGranted) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onGrantClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
