package com.example.digitalmonk.ui.dashboard

import android.content.Intent
import android.net.VpnService
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.service.vpn.DnsVpnService
import com.example.digitalmonk.ui.auth.PinSetupActivity
import com.example.digitalmonk.ui.components.common.SectionLabel
import com.example.digitalmonk.ui.sidebar.formatRemainingTime

private val BgDeep      = Color(0xFF080E1A)
private val BgCard      = Color(0xFF111827)
private val AccentBlue  = Color(0xFF3B82F6)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecond  = Color(0xFF64748B)
private val TextMuted   = Color(0xFF334155)

@Composable
fun DashboardScreen(
    prefs: PrefsManager,
    refreshKey: Long,
    onRefresh: () -> Unit,
    onChangePinClick: () -> Unit       // passed from MainActivity since PinSetupActivity needs Activity context
) {
    val context = LocalContext.current
    var safeSearchEnabled by remember { mutableStateOf(prefs.isSafeSearchEnabled) }
    var blockShorts by remember { mutableStateOf(prefs.isBlockShorts) }
    var showLockDialog by remember { mutableStateOf(false) }
    var showAlwaysOnDialog by remember { mutableStateOf(false) }

    val isLocked = remember(refreshKey) { PrefsManager(context).isSettingsLocked }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            safeSearchEnabled = true
            prefs.setSafeSearchEnabled(true)
            context.startService(Intent(context, DnsVpnService::class.java))
        } else {
            safeSearchEnabled = false
            prefs.setSafeSearchEnabled(false)
            Toast.makeText(context, "VPN Permission is required for Web Filtering", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        SectionLabel("Content Filters")
        Spacer(modifier = Modifier.height(8.dp))

        DashboardToggleCard(
            title = "Block Short Videos",
            description = "Blocks YouTube Shorts, Instagram Reels, TikTok",
            emoji = "📵",
            isEnabled = blockShorts,
            onToggle = { newVal ->
                val prefsCheck = PrefsManager(context)
                if (prefsCheck.isSettingsLocked) {
                    Toast.makeText(context, "Settings are locked for ${formatRemainingTime(prefsCheck.lockUntil - System.currentTimeMillis())}", Toast.LENGTH_LONG).show()
                    return@DashboardToggleCard
                }
                blockShorts = newVal
                prefs.setBlockShorts(newVal)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        DashboardToggleCard(
            title = "SafeSearch & Web Filter",
            description = "Forces SafeSearch on Google/YouTube & blocks adult sites",
            emoji = "🛡️",
            isEnabled = safeSearchEnabled,
            onToggle = { isChecked ->
                val prefsCheck = PrefsManager(context)
                if (prefsCheck.isSettingsLocked) {
                    Toast.makeText(context, "Settings are locked for ${formatRemainingTime(prefsCheck.lockUntil - System.currentTimeMillis())}", Toast.LENGTH_LONG).show()
                    return@DashboardToggleCard
                }
                if (isChecked) {
                    val vpnIntent = VpnService.prepare(context)
                    if (vpnIntent != null) {
                        vpnPermissionLauncher.launch(vpnIntent)
                    } else {
                        safeSearchEnabled = true
                        prefs.setSafeSearchEnabled(true)
                        context.startService(Intent(context, DnsVpnService::class.java))
                    }
                } else {
                    safeSearchEnabled = false
                    prefs.setSafeSearchEnabled(false)
                    context.startService(Intent(context, DnsVpnService::class.java).apply {
                        action = DnsVpnService.ACTION_STOP
                    })
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        DashboardActionCard(
            title = if (isLocked) "🔒 Settings Locked" else "Lock Settings",
            description = if (isLocked) {
                val p = PrefsManager(context)
                val remaining = (p.lockDurationMs - (SystemClock.elapsedRealtime() - p.lockAnchorElapsed)).coerceAtLeast(0L)
                "Unlocks in ${formatRemainingTime(remaining)}"
            } else {
                "Prevent disabling protections for a set period"
            },
            emoji = "⏳",
            onClick = { if (!isLocked) showLockDialog = true }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel("Security")
        Spacer(modifier = Modifier.height(8.dp))

        DashboardActionCard(
            title = "Change PIN",
            description = "Update your parent access PIN",
            emoji = "🔑",
            onClick = onChangePinClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        DashboardActionCard(
            title = "Lockdown VPN (Prevent Bypass)",
            description = "Make the filter permanent so it can't be disabled",
            emoji = "🔒",
            onClick = { showAlwaysOnDialog = true }
        )

        Spacer(modifier = Modifier.height(40.dp))
    }

    if (showLockDialog) {
        LockSettingsDialog(
            onConfirm = { durationMs ->
                val prefsLocal = PrefsManager(context)
                val now = System.currentTimeMillis()
                prefsLocal.lockDurationMs = durationMs
                prefsLocal.lockAnchorElapsed = SystemClock.elapsedRealtime()
                prefsLocal.lockUntil = now + durationMs
                prefsLocal.lastKnownDeviceTime = now
                prefsLocal.lockNtpOffset = Long.MIN_VALUE
                showLockDialog = false
                onRefresh()
                Thread {
                    val ntpTime = com.example.digitalmonk.core.utils.NtpFetcher.fetchNtpTime()
                    if (ntpTime > 0) {
                        val offset = ntpTime - System.currentTimeMillis()
                        prefsLocal.lockNtpOffset = offset
                        prefsLocal.lockUntil = ntpTime + durationMs
                    }
                }.start()
            },
            onDismiss = { showLockDialog = false }
        )
    }

    if (showAlwaysOnDialog) {
        AlwaysOnVpnDialog(
            onOpenSettings = {
                showAlwaysOnDialog = false
                try {
                    context.startActivity(Intent("android.net.vpn.SETTINGS").apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } catch (e: Exception) {
                    Toast.makeText(context, "Go to Settings → Network → VPN", Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { showAlwaysOnDialog = false }
        )
    }
}

// ── Local card composables (dashboard-specific) ───────────────────────────────

@Composable
private fun DashboardToggleCard(
    title: String, description: String, emoji: String,
    isEnabled: Boolean, onToggle: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 15.sp)
                Text(description, color = TextSecond, fontSize = 12.sp)
            }
            Switch(
                checked = isEnabled, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = AccentBlue,
                    uncheckedThumbColor = Color(0xFF94A3B8), uncheckedTrackColor = TextMuted
                )
            )
        }
    }
}

@Composable
private fun DashboardActionCard(
    title: String, description: String, emoji: String, onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 15.sp)
                Text(description, color = TextSecond, fontSize = 12.sp)
            }
            Text("→", color = TextMuted, fontSize = 18.sp)
        }
    }
}

// ── Dialogs (moved here from MainActivity) ────────────────────────────────────

@Composable
private fun AlwaysOnVpnDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🛡️", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Make Filter Permanent", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                    modifier = Modifier.fillMaxWidth().height(48.dp),
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
}

@Composable
private fun LockSettingsDialog(onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var showConfirmStep by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                if (!showConfirmStep) {
                    Text("⏳ Lock Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("Set duration. You will NOT be able to disable any protection during this time.",
                        fontSize = 13.sp, color = Color(0xFF94A3B8), lineHeight = 18.sp)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "Days" to days,
                            "Hours" to hours,
                            "Mins" to minutes
                        ).forEachIndexed { index, (label, value) ->
                            OutlinedTextField(
                                value = value,
                                onValueChange = { when(index) { 0 -> days = it; 1 -> hours = it; 2 -> minutes = it } },
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
                        onClick = { if (totalMs > 0) showConfirmStep = true },
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
                    TextButton(onClick = { showConfirmStep = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("← Go Back", color = Color(0xFF64748B), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}