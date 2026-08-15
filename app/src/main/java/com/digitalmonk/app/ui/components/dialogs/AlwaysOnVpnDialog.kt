package com.digitalmonk.app.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.digitalmonk.app.ui.theme.DigitalMonkTheme

@Composable
fun AlwaysOnVpnDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        AlwaysOnVpnDialogContent(
            onOpenSettings = onOpenSettings,
            onDismiss = onDismiss
        )
    }
}

/**
 * Extracted card content — previewed directly to avoid the Dialog
 * window wrapper which the Android Studio renderer cannot host.
 */
@Composable
private fun AlwaysOnVpnDialogContent(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🛡️", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Make Filter Permanent",
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Enable \"Always-On VPN\" so the filter stays active even after a restart and can't be bypassed.",
                fontSize = 14.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            listOf(
                "1️⃣" to "Tap 'Open VPN Settings' below",
                "2️⃣" to "Find 'Digital Monk Shield'",
                "3️⃣" to "Tap the ⚙️ gear icon next to it",
                "4️⃣" to "Enable 'Always-on VPN'",
                "5️⃣" to "Optional: Enable 'Block without VPN'"
            ).forEach { (emoji, text) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(emoji, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text, fontSize = 13.sp, color = Color(0xFFCBD5E1))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Open VPN Settings", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDismiss) {
                Text("Maybe Later", color = Color(0xFF64748B), fontSize = 13.sp)
            }
        }
    }
}

@Preview(name = "Always-On VPN Dialog", showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun AlwaysOnVpnDialogPreview() {
    DigitalMonkTheme {
        Box(
            modifier = Modifier
                .background(Color(0xFF0F172A))
                .padding(24.dp)
        ) {
            AlwaysOnVpnDialogContent(onOpenSettings = {}, onDismiss = {})
        }
    }
}