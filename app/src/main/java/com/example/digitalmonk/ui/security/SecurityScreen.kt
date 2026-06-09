package com.example.digitalmonk.ui.security

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.ui.components.cards.ToggleCard
import com.example.digitalmonk.ui.components.dialogs.PinGateDialog
import com.example.digitalmonk.ui.components.dialogs.PreventVpnOverrideDialog
import com.example.digitalmonk.ui.components.dialogs.VpnKeepAliveDialog
import com.example.digitalmonk.ui.sidebar.formatRemainingTime

// ── Color palette ─────────────────────────────────────────────────────────────
private val ScreenBg    = Color(0xFF080E1A)
private val CardBg      = Color(0xFF111827)
private val AccentBlue  = Color(0xFF3B82F6)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecond  = Color(0xFF64748B)
private val DividerCol  = Color(0xFF1E293B)

// ─────────────────────────────────────────────────────────────────────────────
//  SecurityScreen  —  public entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SecurityScreen(prefs: PrefsManager) {
    val context = LocalContext.current

    // ── State ─────────────────────────────────────────────────────────────────
    var keepVpnAlive       by remember { mutableStateOf(prefs.isKeepVpnAlive) }
    var preventVpnOverride by remember { mutableStateOf(prefs.isPreventVpnOverride) }

    var showKeepAliveInfo  by remember { mutableStateOf(false) }
    var showPreventDialog  by remember { mutableStateOf(false) }
    var showPinDialog      by remember { mutableStateOf(false) }

    // ── UI ────────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
    ) {
        SecurityScreenHeader()

        Spacer(Modifier.height(8.dp))

        SectionLabel("VPN PROTECTION")

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

        HorizontalDivider(
            color = DividerCol,
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

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

        Spacer(Modifier.height(32.dp))
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
private fun SecurityScreenHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0F2A4A), ScreenBg))
            )
            .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 24.dp)
    ) {
        Column {
            Text("🛡️ Security", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Manage VPN protection settings", fontSize = 13.sp, color = TextSecond)
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF334155),
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────────────────────────────────────

/**
 * SecurityScreen takes PrefsManager (needs Context) — same blocker as PinDialog.
 * We preview the static layout using SecurityScreenContent, passing nullable prefs.
 */
@Composable
private fun SecurityScreenContent(
    keepVpnAlive: Boolean,
    preventVpnOverride: Boolean,
    onKeepVpnToggle: (Boolean) -> Unit,
    onPreventToggle: (Boolean) -> Unit
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
            emoji = "♻️",
            title = "Keep VPN alive",
            subtitle = "Some phones kill VPN willy-nilly. We'll attempt to keep it on for as long as possible.",
            isEnabled = keepVpnAlive,
            onToggle = onKeepVpnToggle
        )

        HorizontalDivider(
            color = DividerCol,
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        ToggleCard(
            emoji = "🔒",
            title = "Prevent VPN override",
            subtitle = "Prevents another VPN from overriding Digital Monk.",
            isEnabled = preventVpnOverride,
            onToggle = onPreventToggle
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Preview(name = "Security Screen — Both Off", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
private fun SecurityScreenPreviewOff() {
    MaterialTheme {
        SecurityScreenContent(
            keepVpnAlive = false,
            preventVpnOverride = false,
            onKeepVpnToggle = {},
            onPreventToggle = {}
        )
    }
}

@Preview(name = "Security Screen — Both On", showBackground = true, backgroundColor = 0xFF080E1A)
@Composable
private fun SecurityScreenPreviewOn() {
    MaterialTheme {
        SecurityScreenContent(
            keepVpnAlive = true,
            preventVpnOverride = true,
            onKeepVpnToggle = {},
            onPreventToggle = {}
        )
    }
}