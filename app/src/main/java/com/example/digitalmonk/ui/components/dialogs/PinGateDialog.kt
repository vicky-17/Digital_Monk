package com.example.digitalmonk.ui.components.dialogs

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.digitalmonk.data.local.prefs.PrefsManager

@Composable
fun PinGateDialog(
    prefs: PrefsManager?, // Nullable for Preview support
    title: String,
    message: String,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("🔒 $title", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(message, fontSize = 13.sp, color = Color(0xFF94A3B8), lineHeight = 18.sp)
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) { pin = it; error = false } },
                    label = { Text("Parent PIN", color = Color(0xFF64748B)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = error,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Spacer(Modifier.height(4.dp))
                    Text("Incorrect PIN", fontSize = 12.sp, color = Color(0xFFEF4444))
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (prefs != null && pin == prefs.pin) onSuccess()
                        else { error = true; pin = "" }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirm", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", color = Color(0xFF64748B), fontSize = 14.sp)
                }
            }
        }
    }
}


// Not a correct way for preview
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
fun PinGateDialogPreview() {
    MaterialTheme {
        Scaffold{
            PinGateDialog(
                prefs = null,
                title = "Disable VPN Protection",
                message = "Enter your parent PIN to turn off VPN override protection.",
                onSuccess = {},
                onDismiss = {}
            )
        }

    }
}