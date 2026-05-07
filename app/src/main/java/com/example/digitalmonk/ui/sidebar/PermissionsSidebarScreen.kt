package com.example.digitalmonk.ui.sidebar

import android.provider.Settings
import androidx.core.content.edit
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.digitalmonk.core.utils.PersistenceManager
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.receiver.MonkDeviceAdminReceiver
import com.example.digitalmonk.ui.PermissionsState

import com.example.digitalmonk.ui.components.cards.PermissionCard
import com.example.digitalmonk.ui.components.cards.ToggleCard
import com.example.digitalmonk.ui.components.dialogs.VpnKeepAliveDialog
import com.example.digitalmonk.ui.components.dialogs.PreventVpnOverrideDialog
import com.example.digitalmonk.ui.components.dialogs.PinGateDialog


// ── Color palette (mirrors MainActivity palette — keep in sync or extract to a shared Theme file later) ──
private val BgCard       = Color(0xFF111827)
private val AccentBlue   = Color(0xFF3B82F6)
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

            PermissionCard(
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

            PermissionCard(
                emoji = "🔋", title = "Battery Optimization",
                subtitle = "Keeps app alive in background",
                isGranted = permissionsState.isBatteryExempt, isCritical = true,
                onAction = { batteryLauncher.launch(PersistenceManager.buildBatteryOptimizationIntent(context)) }
            )

            SidebarDivider()

            PermissionCard(
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
                    SidebarDivider()
                    PermissionCard(
                        emoji = "🪟",
                        title = "Background Pop-up (MIUI)",
                        subtitle = "Allow overlay to appear over blocked apps",
                        isGranted = permissionsState.visitedMiuiBgPopup,
                        isCritical = true,
                        onAction = {
                            context.getSharedPreferences("monk_prefs", android.content.Context.MODE_PRIVATE)
                                .edit { putBoolean("visited_miui_bg_popup", true) }
                            onRefresh()
                            context.startActivity(bgPopupIntent)
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Section: Important ────────────────────────────────────────
            SidebarSectionLabel("IMPORTANT")

            PermissionCard(
                emoji = "🛡️", title = "Device Admin",
                subtitle = "Prevents app from being uninstalled",
                isGranted = permissionsState.isDeviceAdmin, isCritical = false,
                onAction = { deviceAdminLauncher.launch(MonkDeviceAdminReceiver.buildActivationIntent(context)) }
            )

            SidebarDivider()

            PermissionCard(
                emoji = "📊", title = "Usage Access",
                subtitle = "Tracks screen time per app",
                isGranted = permissionsState.hasUsageStats, isCritical = false,
                onAction = { context.startActivity(PersistenceManager.buildUsageStatsIntent()) }
            )

            SidebarDivider()

            PermissionCard(
                emoji = "🔔", title = "Notifications",
                subtitle = "Alerts when content is blocked",
                isGranted = permissionsState.hasNotification, isCritical = false,
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                }
            )

            Spacer(Modifier.height(24.dp))

            // ── Section: VPN Settings ─────────────────────────────────────
            SidebarSectionLabel("VPN SETTINGS")

            ToggleCard(
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
                        return@ToggleCard
                    }
                    if (newValue) showKeepAliveInfo = true
                    else {
                        keepVpnAlive = false
                        prefs.isKeepVpnAlive = false
                    }
                }
            )

            SidebarDivider()

            ToggleCard(
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
                        return@ToggleCard
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
                prefs.isKeepVpnAlive = true
            },
            onDismiss = { showKeepAliveInfo = false }
        )
    }

    if (showPreventDialog) {
        PreventVpnOverrideDialog(
            onConfirm = {
                showPreventDialog = false
                preventVpnOverride = true
                prefs.isPreventVpnOverride = true
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
                prefs.isPreventVpnOverride = false
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