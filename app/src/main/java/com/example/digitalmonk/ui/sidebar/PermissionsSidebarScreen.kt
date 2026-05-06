package com.example.digitalmonk.ui.sidebar

import android.os.Build
import android.provider.Settings
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.digitalmonk.core.utils.PersistenceManager
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.receiver.MonkDeviceAdminReceiver
import com.example.digitalmonk.ui.PermissionsState

// ── Color palette (mirrors MainActivity palette — keep in sync or extract to a shared Theme file later) ──
private val BgCard       = Color(0xFF111827)
private val AccentBlue   = Color(0xFF3B82F6)
private val AccentCyan   = Color(0xFF06B6D4)
private val AccentGreen  = Color(0xFF10B981)
private val AccentAmber  = Color(0xFFF59E0B)
private val AccentRed    = Color(0xFFEF4444)
private val TextPrimary  = Color(0xFFF1F5F9)
private val TextSecond   = Color(0xFF64748B)
private val TextMuted    = Color(0xFF334155)
private val Divider      = Color(0xFF1E293B)
private val SidebarBg    = Color(0xFF0B1322)
private val SidebarEdge  = Color(0xFF1E3A5F)

// ─────────────────────────────────────────────────────────────────────────────
// PermissionsSidebar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PermissionsSidebar(
    prefs: PrefsManager,
    permissionsState: PermissionsState,
    onRefresh: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    // ── VPN Settings state ────────────────────────────────────────────────
    var keepVpnAlive       by remember { mutableStateOf(prefs.isKeepVpnAlive) }
    var preventVpnOverride by remember { mutableStateOf(prefs.isPreventVpnOverride) }

    // Dialog visibility state
    var showPinDialog      by remember { mutableStateOf(false) }
    var showKeepAliveInfo  by remember { mutableStateOf(false) }
    var showPreventDialog  by remember { mutableStateOf(false) }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onRefresh() }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onRefresh() }

    val hasOemAutostart = PersistenceManager.hasOemAutostartSetting(context)
    val grantedCount = listOf(
        permissionsState.isAccessibilityOn,
        permissionsState.isBatteryExempt,
        permissionsState.canDrawOverlays,
        permissionsState.isDeviceAdmin,
        permissionsState.hasUsageStats,
        permissionsState.hasNotification,
        if (hasOemAutostart) permissionsState.visitedAutostart else true
    ).count { it }

    val totalCount = if (hasOemAutostart) 7 else 6

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(310.dp)
            .shadow(32.dp, RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .background(
                brush = Brush.horizontalGradient(listOf(SidebarBg, BgCard)),
                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            )
            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
    ) {
        // Decorative right edge glow
        Canvas(modifier = Modifier.fillMaxHeight().width(3.dp).align(Alignment.CenterEnd)) {
            drawLine(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        SidebarEdge,
                        AccentBlue.copy(alpha = 0.6f),
                        SidebarEdge,
                        Color.Transparent
                    )
                ),
                start = Offset(0f, 0f),
                end = Offset(0f, size.height),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────
            SidebarHeader(
                grantedCount = grantedCount,
                totalCount = totalCount,
                onClose = onClose
            )

            Spacer(Modifier.height(8.dp))

            // ── Section: Critical ─────────────────────────────────────────
            SidebarSectionLabel("CRITICAL")

            SidebarPermissionRow(
                emoji = "♿", title = "Accessibility Service",
                subtitle = "Required for app & Shorts blocking",
                isGranted = permissionsState.isAccessibilityOn, isCritical = true,
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).also {
                        it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            )

            SidebarDivider()

            SidebarPermissionRow(
                emoji = "🔋", title = "Battery Optimization",
                subtitle = "Keeps app alive in background",
                isGranted = permissionsState.isBatteryExempt, isCritical = true,
                onAction = { batteryLauncher.launch(PersistenceManager.buildBatteryOptimizationIntent(context)) }
            )

            SidebarDivider()

            SidebarPermissionRow(
                emoji = "🪟", title = "Display Over Other Apps",
                subtitle = "Shows block screen on restricted apps",
                isGranted = permissionsState.canDrawOverlays, isCritical = true,
                onAction = { context.startActivity(PersistenceManager.buildOverlayPermissionIntent(context)) }
            )

            // MIUI Background Pop-up (Xiaomi only)
            val isXiaomiSidebar = PersistenceManager.detectOem() == PersistenceManager.OemType.XIAOMI
            if (isXiaomiSidebar) {
                val bgPopupIntent = remember { PersistenceManager.buildMiuiBackgroundPopupIntent(context) }
                if (bgPopupIntent != null) {
                    val sharedPrefs = remember {
                        context.getSharedPreferences("monk_prefs", android.content.Context.MODE_PRIVATE)
                    }
                    SidebarDivider()
                    SidebarPermissionRow(
                        emoji = "🪟",
                        title = "Background Pop-up (MIUI)",
                        subtitle = "Allow overlay to appear over blocked apps",
                        isGranted = permissionsState.visitedMiuiBgPopup,
                        isCritical = true,
                        onAction = {
                            sharedPrefs.edit().putBoolean("visited_miui_bg_popup", true).apply()
                            onRefresh()
                            context.startActivity(bgPopupIntent)
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Section: Important ────────────────────────────────────────
            SidebarSectionLabel("IMPORTANT")

            SidebarPermissionRow(
                emoji = "🛡️", title = "Device Admin",
                subtitle = "Prevents app from being uninstalled",
                isGranted = permissionsState.isDeviceAdmin, isCritical = false,
                onAction = { deviceAdminLauncher.launch(MonkDeviceAdminReceiver.buildActivationIntent(context)) }
            )

            SidebarDivider()

            SidebarPermissionRow(
                emoji = "📊", title = "Usage Access",
                subtitle = "Tracks screen time per app",
                isGranted = permissionsState.hasUsageStats, isCritical = false,
                onAction = { context.startActivity(PersistenceManager.buildUsageStatsIntent()) }
            )

            SidebarDivider()

            SidebarPermissionRow(
                emoji = "🔔", title = "Notifications",
                subtitle = "Alerts when content is blocked",
                isGranted = permissionsState.hasNotification, isCritical = false,
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            // ── Section: VPN Settings ─────────────────────────────────────
            SidebarSectionLabel("VPN SETTINGS")

            SidebarToggleRow(
                emoji = "♻️",
                title = "Keep VPN alive",
                subtitle = "Some phones kill VPN willy-nilly. We'll attempt to keep it on for as long as possible.",
                isEnabled = keepVpnAlive,
                onToggle = { newValue ->
                    val prefsCheck = PrefsManager(context)
                    if (prefsCheck.isSettingsLocked) {
                        Toast.makeText(
                            context,
                            "Settings are locked for ${formatRemainingTime(prefsCheck.lockUntil - System.currentTimeMillis())}",
                            Toast.LENGTH_LONG
                        ).show()
                        return@SidebarToggleRow
                    }
                    if (newValue) showKeepAliveInfo = true
                    else {
                        keepVpnAlive = false
                        prefs.setKeepVpnAlive(false)
                    }
                }
            )

            SidebarDivider()

            SidebarToggleRow(
                emoji = "🔒",
                title = "Prevent VPN override",
                subtitle = "Prevents another VPN from overriding Digital Monk.",
                isEnabled = preventVpnOverride,
                onToggle = { newValue ->
                    val prefsCheck = PrefsManager(context)
                    if (prefsCheck.isSettingsLocked) {
                        Toast.makeText(
                            context,
                            "Settings are locked for ${formatRemainingTime(prefsCheck.lockUntil - System.currentTimeMillis())}",
                            Toast.LENGTH_LONG
                        ).show()
                        return@SidebarToggleRow
                    }
                    if (newValue) showPreventDialog = true
                    else showPinDialog = true
                }
            )

            // Data policy note
            Spacer(Modifier.height(16.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            ) {
                Text(
                    "We don't ask for data we don't need.",
                    fontSize = 11.sp, color = TextSecond, textAlign = TextAlign.Center
                )
                Text(
                    "Your data stays on this device.",
                    fontSize = 11.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

            // Refresh hint
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Divider)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ℹ️", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Return to the app after granting each permission. Status updates automatically.",
                    fontSize = 11.sp, color = TextSecond, lineHeight = 16.sp
                )
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showKeepAliveInfo) {
        VpnKeepAliveDialog(
            onConfirm = {
                showKeepAliveInfo = false
                keepVpnAlive = true
                prefs.setKeepVpnAlive(true)
            },
            onDismiss = { showKeepAliveInfo = false }
        )
    }

    if (showPreventDialog) {
        PreventVpnOverrideDialog(
            onConfirm = {
                showPreventDialog = false
                preventVpnOverride = true
                prefs.setPreventVpnOverride(true)
            },
            onDismiss = { showPreventDialog = false }
        )
    }

    if (showPinDialog) {
        PinGateDialog(
            prefs = prefs,
            title = "Disable VPN Protection",
            message = "Enter your parent PIN to turn off VPN override protection.",
            onSuccess = {
                showPinDialog = false
                preventVpnOverride = false
                prefs.setPreventVpnOverride(false)
            },
            onDismiss = { showPinDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SidebarHeader(grantedCount: Int, totalCount: Int, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F2A4A), SidebarBg)))
            .padding(start = 20.dp, end = 16.dp, top = 52.dp, bottom = 20.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                Brush.radialGradient(listOf(AccentBlue.copy(0.25f), Color.Transparent)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🛡️", fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Permissions & VPN", fontSize = 11.sp, color = TextSecond, letterSpacing = 0.5.sp)
                    }
                }
                IconButton(onClick = onClose) {
                    Text("✕", fontSize = 16.sp, color = TextSecond)
                }
            }

            Spacer(Modifier.height(16.dp))

            val progress = grantedCount.toFloat() / totalCount.toFloat()
            val progressColor = when {
                progress >= 1f   -> AccentGreen
                progress >= 0.5f -> AccentAmber
                else             -> AccentRed
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(TextMuted)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = progress)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(progressColor.copy(0.7f), progressColor)))
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "$grantedCount / $totalCount",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = progressColor
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (grantedCount == totalCount) "All permissions granted ✓"
                else "${totalCount - grantedCount} permission${if (totalCount - grantedCount != 1) "s" else ""} need attention",
                fontSize = 11.sp,
                color = if (grantedCount == totalCount) AccentGreen else AccentAmber
            )
        }
    }
}

@Composable
internal fun SidebarSectionLabel(label: String) {
    Text(
        label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 20.dp, bottom = 4.dp)
    )
}

@Composable
internal fun SidebarDivider() {
    HorizontalDivider(
        color = Divider,
        thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

@Composable
internal fun SidebarPermissionRow(
    emoji: String,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    isCritical: Boolean,
    onAction: () -> Unit
) {
    val bgColor = if (isGranted) Color(0xFF0A1F14) else Color.Transparent
    val chipColor = when {
        isGranted  -> AccentGreen
        isCritical -> AccentRed
        else       -> AccentAmber
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(enabled = !isGranted, onClick = onAction)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (isGranted) AccentGreen.copy(0.1f)
                    else if (isCritical) AccentRed.copy(0.08f)
                    else AccentAmber.copy(0.08f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 18.sp)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = TextSecond)
        }

        Spacer(Modifier.width(8.dp))

        if (isGranted) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AccentGreen.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", fontSize = 12.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(chipColor.copy(0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Fix →", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = chipColor)
            }
        }
    }
}

@Composable
internal fun SidebarToggleRow(
    emoji: String,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isEnabled) Color(0xFF0A1520) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (isEnabled) AccentCyan.copy(0.12f) else TextMuted.copy(0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 18.sp)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = TextSecond, lineHeight = 15.sp)
        }

        Spacer(Modifier.width(8.dp))

        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentCyan,
                uncheckedThumbColor = Color(0xFF64748B),
                uncheckedTrackColor = TextMuted
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VPN Keep Alive info dialog
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// Prevent VPN Override confirm dialog
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// PIN gate dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PinGateDialog(
    prefs: PrefsManager,
    title: String,
    message: String,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin   by remember { mutableStateOf("") }
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
                        if (pin == prefs.getPin()) onSuccess()
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

// ─────────────────────────────────────────────────────────────────────────────
// Shared utility — also used by DashboardContent in MainActivity
// Move to a shared utils file (e.g. ui/utils/TimeUtils.kt) when you extract DashboardContent
// ─────────────────────────────────────────────────────────────────────────────

fun formatRemainingTime(ms: Long): String {
    val totalSec = ms / 1000
    val d = totalSec / 86400
    val h = (totalSec % 86400) / 3600
    val m = (totalSec % 3600) / 60
    return buildString {
        if (d > 0) append("${d}d ")
        if (h > 0) append("${h}h ")
        append("${m}m")
    }.trim()
}



@Preview(showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
fun PermissionsSidebarPreview(){
    val context = LocalContext.current

    val dummyPrefs = remember { PrefsManager(context) }
    val mockState = PermissionsState(
        isAccessibilityOn = false,
        isBatteryExempt = true,
        canDrawOverlays = false,
        isDeviceAdmin = false,
        hasUsageStats = true,
        hasNotification = false,
        visitedAutostart = false,
        visitedMiuiPower = false,
        visitedMiuiBgPopup = false
    )


    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF080E1A))
    ) {
        PermissionsSidebar(
            prefs = dummyPrefs,
            permissionsState = mockState,
            onRefresh = { },
            onClose = { }
        )

    }
}




