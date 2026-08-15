package com.digitalmonk.app.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.digitalmonk.app.ui.theme.DigitalMonkTheme

@Composable
fun LockSettingsDialog(onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var showConfirmStep by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        LockSettingsDialogContent(
            days = days,
            hours = hours,
            minutes = minutes,
            showConfirmStep = showConfirmStep,
            onDaysChange = { days = it },
            onHoursChange = { hours = it },
            onMinutesChange = { minutes = it },
            onNext = { showConfirmStep = true },
            onConfirm = onConfirm,
            onBack = { showConfirmStep = false },
            onDismiss = onDismiss
        )
    }
}

/**
 * Extracted card content — kept separate so @Preview can render it
 * without the Dialog window wrapper, which the preview renderer can't host.
 */
@Composable
private fun LockSettingsDialogContent(
    days: String,
    hours: String,
    minutes: String,
    showConfirmStep: Boolean,
    onDaysChange: (String) -> Unit,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onNext: () -> Unit,
    onConfirm: (Long) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            if (!showConfirmStep) {
                Text("⏳ Lock Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Set duration. You will NOT be able to disable any protection during this time.",
                    fontSize = 13.sp, color = Color(0xFF94A3B8), lineHeight = 18.sp
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("Days", days, onDaysChange),
                        Triple("Hours", hours, onHoursChange),
                        Triple("Mins", minutes, onMinutesChange)
                    ).forEach { (label, value, onChange) ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = onChange,
                            label = { Text(label, color = Color(0xFF64748B)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                val totalMs = (days.toLongOrNull() ?: 0L) * 86_400_000L +
                        (hours.toLongOrNull() ?: 0L) * 3_600_000L +
                        (minutes.toLongOrNull() ?: 0L) * 60_000L
                Button(
                    onClick = { if (totalMs > 0) onNext() },
                    enabled = totalMs > 0,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Next →", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", color = Color(0xFF64748B), fontSize = 14.sp)
                }
            } else {
                val d = days.toLongOrNull() ?: 0L
                val h = hours.toLongOrNull() ?: 0L
                val m = minutes.toLongOrNull() ?: 0L
                val totalMs = d * 86_400_000L + h * 3_600_000L + m * 60_000L
                Text("⚠️ Are you sure?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                Spacer(Modifier.height(12.dp))
                Text(
                    "You cannot disable any protections for ${if (d > 0) "${d}d " else ""}${if (h > 0) "${h}h " else ""}${m}m. This cannot be undone.",
                    fontSize = 14.sp, color = Color(0xFF94A3B8), lineHeight = 20.sp
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { onConfirm(totalMs) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("🔒 Confirm Lock", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("← Go Back", color = Color(0xFF64748B), fontSize = 14.sp)
                }
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────

@Preview(name = "Step 1 — Duration Input", showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun LockSettingsDialogStep1Preview() {
    DigitalMonkTheme {
        Box(
            modifier = Modifier
                .background(Color(0xFF0F172A))
                .padding(24.dp)
        ) {
            LockSettingsDialogContent(
                days = "",
                hours = "",
                minutes = "",
                showConfirmStep = false,
                onDaysChange = {},
                onHoursChange = {},
                onMinutesChange = {},
                onNext = {},
                onConfirm = {},
                onBack = {},
                onDismiss = {}
            )
        }
    }
}

@Preview(name = "Step 2 — Confirm Lock", showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun LockSettingsDialogStep2Preview() {
    DigitalMonkTheme {
        Box(
            modifier = Modifier
                .background(Color(0xFF0F172A))
                .padding(24.dp)
        ) {
            LockSettingsDialogContent(
                days = "1",
                hours = "2",
                minutes = "30",
                showConfirmStep = true,
                onDaysChange = {},
                onHoursChange = {},
                onMinutesChange = {},
                onNext = {},
                onConfirm = {},
                onBack = {},
                onDismiss = {}
            )
        }
    }
}