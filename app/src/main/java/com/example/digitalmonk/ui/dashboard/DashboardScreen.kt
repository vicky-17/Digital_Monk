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
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.service.vpn.DnsVpnService
import com.example.digitalmonk.ui.components.common.SectionLabel
import com.example.digitalmonk.ui.sidebar.formatRemainingTime
import com.example.digitalmonk.ui.components.dialogs.AlwaysOnVpnDialog
import com.example.digitalmonk.ui.components.dialogs.LockSettingsDialog

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
                        showAlwaysOnDialog = true
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





