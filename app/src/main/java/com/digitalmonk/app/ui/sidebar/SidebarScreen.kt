package com.digitalmonk.app.ui.sidebar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.digitalmonk.app.data.local.prefs.PrefsManager
import com.digitalmonk.app.ui.PermissionsState

// ── Color palette ─────────────────────────────────────────────────────────────
private val BgCard      = Color(0xFF111827)
private val AccentBlue  = Color(0xFF3B82F6)
private val AccentGreen = Color(0xFF10B981)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecond  = Color(0xFF64748B)
private val TextMuted   = Color(0xFF334155)
private val Divider     = Color(0xFF1E293B)
private val SidebarBg   = Color(0xFF0B1322)
private val SidebarEdge = Color(0xFF1E3A5F)

@Composable
fun PermissionsSidebar(
    prefs: PrefsManager,
    permissionsState: PermissionsState,
    onRefresh: () -> Unit,
    onClose: () -> Unit
) {
    val vpnSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onRefresh() }

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
            SidebarHeader(onClose = onClose)

            // NOTE: "Keep VPN alive" and "Prevent VPN override" toggles have
            // been moved to SecurityScreen. Add SecurityScreen navigation here
            // if needed (e.g. a shortcut button to open it).

            // ── Footer ────────────────────────────────────────────────────
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
                    "Return to the app.",
                    fontSize = 11.sp, color = TextSecond, lineHeight = 16.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SidebarHeader(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F2A4A), SidebarBg)))
            .padding(start = 20.dp, end = 16.dp, top = 52.dp, bottom = 20.dp)
    ) {
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
                    Text("⏸️", fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("SideBar", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Put quick navigation buttons here.", fontSize = 11.sp, color = TextSecond, letterSpacing = 0.5.sp)
                }
            }
            IconButton(onClick = onClose) {
                Text("✕", fontSize = 16.sp, color = TextSecond)
            }
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
private fun PermissionsSidebarPreview() {
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
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080E1A))) {
        PermissionsSidebar(
            prefs = dummyPrefs,
            permissionsState = mockState,
            onRefresh = {},
            onClose = {}
        )
    }
}