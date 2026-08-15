package com.digitalmonk.app.ui.security

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitalmonk.app.data.local.prefs.PrefsManager
import com.digitalmonk.app.ui.components.cards.ToggleCard
import com.digitalmonk.app.ui.components.dialogs.ConfirmDialog
import com.digitalmonk.app.ui.components.dialogs.PinGateDialog
import com.digitalmonk.app.ui.components.dialogs.PreventVpnOverrideDialog
import com.digitalmonk.app.ui.components.dialogs.VpnKeepAliveDialog
import com.digitalmonk.app.ui.sidebar.formatRemainingTime

// ── Color palette ─────────────────────────────────────────────────────────────
private val ScreenBg   = Color(0xFF080E1A)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecond  = Color(0xFF64748B)
private val DividerCol  = Color(0xFF1E293B)

// ─────────────────────────────────────────────────────────────────────────────
//  SecurityScreen  —  public entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SecurityScreen(prefs: PrefsManager) {
    val context = LocalContext.current
    val viewModel = remember { SecurityViewModel(prefs) }

    val isPermissionBlockEnabled by viewModel.isPermissionBlockEnabled.collectAsState()
    val showConfirmDialog by viewModel.showConfirmDialog.collectAsState()

    // ── State ─────────────────────────────────────────────────────────────────
    var keepVpnAlive            by remember { mutableStateOf(prefs.isKeepVpnAlive) }
    var preventVpnOverride      by remember { mutableStateOf(prefs.isPreventVpnOverride) }
    var antiUninstallEnabled    by remember { mutableStateOf(prefs.isAntiUninstallEnabled) }

    // ── Dialog visibility ─────────────────────────────────────────────────────
    var showKeepAliveInfoDialog     by remember { mutableStateOf(false) }
    var showPreventVpnDialog        by remember { mutableStateOf(false) }
    var showDisableVpnPinDialog     by remember { mutableStateOf(false) }
    var showAntiUninstallPinDialog  by remember { mutableStateOf(false) }

    // ── UI ────────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
    ) {
        SecurityScreenHeader()

        Spacer(Modifier.height(8.dp))

        // ── VPN Protection section ────────────────────────────────────────────
        SectionLabel("VPN PROTECTION")

        ToggleCard(
            emoji    = "♻️",
            title    = "Keep VPN Alive",
            subtitle = "Some phones kill VPN unexpectedly. We'll attempt to keep it on for as long as possible.",
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
                if (newValue) {
                    showKeepAliveInfoDialog = true
                } else {
                    keepVpnAlive = false
                    prefs.isKeepVpnAlive = false
                }
            }
        )

        HorizontalDivider(
            color     = DividerCol,
            thickness = 0.5.dp,
            modifier  = Modifier.padding(horizontal = 20.dp)
        )

        ToggleCard(
            emoji    = "🔒",
            title    = "Prevent VPN Override",
            subtitle = "Prevents another VPN app from overriding Digital Monk's filter.",
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
                if (newValue) {
                    showPreventVpnDialog = true
                } else {
                    // Disabling is the dangerous direction — require PIN
                    showDisableVpnPinDialog = true
                }
            }
        )

        Spacer(Modifier.height(24.dp))

        // ── Anti-Uninstall section ────────────────────────────────────────────
        SectionLabel("ANTI-UNINSTALL")

        ToggleCard(
            emoji    = "🛡️",
            title    = "Prevent Settings Tampering",
            subtitle = "Blocks access to Force Stop, Uninstall, and Device Admin pages for Digital Monk.",
            isEnabled = antiUninstallEnabled,
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
                if (newValue) {
                    // Turning ON is always safe — no PIN required
                    antiUninstallEnabled = true
                    prefs.isAntiUninstallEnabled = true
                } else {
                    // Turning OFF is dangerous — require PIN
                    showAntiUninstallPinDialog = true
                }
            }
        )

        Spacer(Modifier.height(24.dp))

        SectionLabel("STRICT PERMISSION ENFORCEMENT")

        ToggleCard(
            emoji     = "⚠️",
            title     = "Strict Permission Blocking",
            subtitle  = "Block entire screen if required permissions are missing.",
            isEnabled = isPermissionBlockEnabled,
            onToggle  = { isChecked ->
                viewModel.onToggleClicked(isChecked)
            }
        )

        Spacer(Modifier.height(40.dp))
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (showConfirmDialog) {
        ConfirmDialog(
            title = "Change Security Setting",
            message = "Are you sure you want to change the permission blocking behavior? Disabling this might prevent the app from enforcing rules effectively.",
            onConfirm = { viewModel.confirmToggle() },
            onDismiss = { viewModel.dismissDialog() }
        )
    }

    if (showKeepAliveInfoDialog) {
        VpnKeepAliveDialog(
            onConfirm = {
                showKeepAliveInfoDialog = false
                keepVpnAlive = true
                prefs.isKeepVpnAlive = true
            },
            onDismiss = { showKeepAliveInfoDialog = false }
        )
    }

    if (showPreventVpnDialog) {
        PreventVpnOverrideDialog(
            onConfirm = {
                showPreventVpnDialog = false
                preventVpnOverride = true
                prefs.isPreventVpnOverride = true
            },
            onDismiss = { showPreventVpnDialog = false }
        )
    }

    if (showDisableVpnPinDialog) {
        PinGateDialog(
            prefs   = prefs,
            title   = "Disable VPN Override Protection",
            message = "Enter your parent PIN to allow other VPN apps to override Digital Monk.",
            onSuccess = {
                showDisableVpnPinDialog = false
                preventVpnOverride = false
                prefs.isPreventVpnOverride = false
            },
            onDismiss = { showDisableVpnPinDialog = false }
        )
    }

    if (showAntiUninstallPinDialog) {
        PinGateDialog(
            prefs   = prefs,
            title   = "Disable Anti-Uninstall Protection",
            message = "Turning this off allows access to Force Stop, Uninstall, and Device Admin pages for Digital Monk. Enter your PIN to confirm.",
            onSuccess = {
                showAntiUninstallPinDialog = false
                antiUninstallEnabled = false
                prefs.isAntiUninstallEnabled = false
            },
            onDismiss = { showAntiUninstallPinDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SecurityScreenHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F2A4A), ScreenBg)))
            .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 24.dp)
    ) {
        Column {
            Text("🛡️ Security", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Manage protection and VPN settings", fontSize = 13.sp, color = TextSecond)
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text          = label,
        fontSize      = 10.sp,
        fontWeight    = FontWeight.Bold,
        color         = Color(0xFF334155),
        letterSpacing = 1.5.sp,
        modifier      = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews — stateless, no PrefsManager/Context needed
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SecurityScreenContent(
    keepVpnAlive:         Boolean,
    preventVpnOverride:   Boolean,
    antiUninstallEnabled: Boolean,
    onKeepVpnToggle:      (Boolean) -> Unit,
    onPreventToggle:      (Boolean) -> Unit,
    onAntiUninstallToggle:(Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        SecurityScreenHeader()
        Spacer(Modifier.height(8.dp))

        SectionLabel("VPN PROTECTION")
        ToggleCard(
            emoji    = "♻️",
            title    = "Keep VPN Alive",
            subtitle = "Some phones kill VPN unexpectedly. We'll attempt to keep it on for as long as possible.",
            isEnabled = keepVpnAlive,
            onToggle  = onKeepVpnToggle
        )
        HorizontalDivider(
            color     = DividerCol,
            thickness = 0.5.dp,
            modifier  = Modifier.padding(horizontal = 20.dp)
        )
        ToggleCard(
            emoji    = "🔒",
            title    = "Prevent VPN Override",
            subtitle = "Prevents another VPN app from overriding Digital Monk's filter.",
            isEnabled = preventVpnOverride,
            onToggle  = onPreventToggle
        )

        Spacer(Modifier.height(24.dp))

        SectionLabel("ANTI-UNINSTALL")
        ToggleCard(
            emoji    = "🛡️",
            title    = "Prevent Settings Tampering",
            subtitle = "Blocks access to Force Stop, Uninstall, and Device Admin pages for Digital Monk.",
            isEnabled = antiUninstallEnabled,
            onToggle  = onAntiUninstallToggle
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Preview(name = "Security — All Off", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
private fun SecurityScreenPreviewAllOff() {
    MaterialTheme {
        SecurityScreenContent(
            keepVpnAlive          = false,
            preventVpnOverride    = false,
            antiUninstallEnabled  = false,
            onKeepVpnToggle       = {},
            onPreventToggle       = {},
            onAntiUninstallToggle = {}
        )
    }
}

@Preview(name = "Security — All On", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
private fun SecurityScreenPreviewAllOn() {
    MaterialTheme {
        SecurityScreenContent(
            keepVpnAlive          = true,
            preventVpnOverride    = true,
            antiUninstallEnabled  = true,
            onKeepVpnToggle       = {},
            onPreventToggle       = {},
            onAntiUninstallToggle = {}
        )
    }
}