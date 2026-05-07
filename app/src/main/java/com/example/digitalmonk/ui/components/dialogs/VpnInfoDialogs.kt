package com.example.digitalmonk.ui.components.dialogs

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun VpnKeepAliveDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Keep VPN alive", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Some device types kill the VPN under certain circumstances. Here are some popular situations that can kill the VPN.",
                    fontSize = 13.sp, color = Color(0xFF94A3B8), lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                listOf("Low battery", "Low CPU/memory", "Ultra-fast charging").forEach { item ->
                    Text("- $item", fontSize = 13.sp, color = Color(0xFFCBD5E1))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "By turning this on, Digital Monk will check if the VPN is on when you turn on your screen. It will proceed to turn on the VPN if it is off.",
                    fontSize = 13.sp, color = Color(0xFF94A3B8), lineHeight = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "NOTE: If you have pin protect turned on, you will be asked for the pin before you can turn off this feature.",
                    fontSize = 12.sp, color = Color(0xFF64748B), lineHeight = 17.sp
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Turn it on", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Go back", color = Color(0xFF64748B), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun PreventVpnOverrideDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Prevent VPN override", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Turning on this feature will make it impossible to use other VPNs.",
                    fontSize = 13.sp, color = Color(0xFF94A3B8), lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "NOTE: If you have pin protect turned on, you will be asked for the pin before you can turn off this feature.",
                    fontSize = 12.sp, color = Color(0xFF64748B), lineHeight = 17.sp
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Turn it on", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Go back", color = Color(0xFF64748B), fontSize = 14.sp)
                }
            }
        }
    }
}


// Not a correct way of preview

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
fun VpnDialogsPreview() {
    MaterialTheme {
        Scaffold{
            VpnKeepAliveDialog(onConfirm = {}, onDismiss = {})
        }
    }
}