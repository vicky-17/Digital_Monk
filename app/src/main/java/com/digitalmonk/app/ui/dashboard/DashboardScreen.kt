package com.digitalmonk.app.ui.dashboard

import android.content.Intent
import android.net.VpnService
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitalmonk.app.data.local.prefs.PrefsManager
import com.digitalmonk.app.data.models.AppUsageInfo
import com.digitalmonk.app.service.vpn.DnsVpnService
import com.digitalmonk.app.ui.components.common.SectionLabel
import com.digitalmonk.app.ui.sidebar.formatRemainingTime
import com.digitalmonk.app.ui.components.dialogs.AlwaysOnVpnDialog
import com.digitalmonk.app.ui.components.dialogs.LockSettingsDialog

private val BgDeep      = Color(0xFF04040c)
private val AccentBlue  = Color(0xFF3B82F6)
private val AccentPink  = Color(0xFFEC4899)
private val AccentTeal  = Color(0xFF14B8A6)
private val AccentViolet = Color(0xFF8B5CF6)
private val TextPrimary = Color(0xFFf5f6fb)
private val TextSecond  = Color(0xFFf5f6fb).copy(alpha = 0.62f)
private val TextMuted   = Color(0xFFf5f6fb).copy(alpha = 0.38f)

@Composable
fun DashboardScreen(
    prefs: PrefsManager,
    refreshKey: Long,
    onRefresh: () -> Unit,
    onNavigateToUsageStats: () -> Unit,
    usageViewModel: UsageViewModel
) {
    val context = LocalContext.current
    var safeSearchEnabled by remember { mutableStateOf(prefs.isSafeSearchEnabled) }
    var blockShorts by remember { mutableStateOf(prefs.isBlockShorts) }
    var showLockDialog by remember { mutableStateOf(false) }
    var showAlwaysOnDialog by remember { mutableStateOf(false) }

    val isLocked = remember(refreshKey) { PrefsManager(context).isSettingsLocked }
    
    LaunchedEffect(refreshKey) {
        usageViewModel.refreshStats()
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            safeSearchEnabled = true
            prefs.isSafeSearchEnabled = true
            context.startService(Intent(context, DnsVpnService::class.java))
        } else {
            safeSearchEnabled = false
            prefs.isSafeSearchEnabled = false
            Toast.makeText(context, "VPN Permission is required for Web Filtering", Toast.LENGTH_LONG).show()
        }
    }

    DashboardContent(
        safeSearchEnabled = safeSearchEnabled,
        blockShorts = blockShorts,
        isLocked = isLocked,
        onSafeSearchToggle = { isChecked ->
            val prefsCheck = PrefsManager(context)
            if (prefsCheck.isSettingsLocked) {
                Toast.makeText(context, "Settings are locked for ${formatRemainingTime(prefsCheck.lockUntil - System.currentTimeMillis())}", Toast.LENGTH_LONG).show()
                return@DashboardContent
            }
            if (isChecked) {
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent != null) {
                    vpnPermissionLauncher.launch(vpnIntent)
                } else {
                    safeSearchEnabled = true
                    prefs.isSafeSearchEnabled = true
                    context.startService(Intent(context, DnsVpnService::class.java))
                    showAlwaysOnDialog = true
                }
            } else {
                safeSearchEnabled = false
                prefs.isSafeSearchEnabled = false
                context.startService(Intent(context, DnsVpnService::class.java).apply {
                    action = DnsVpnService.ACTION_STOP
                })
            }
        },
        onBlockShortsToggle = { newVal ->
            val prefsCheck = PrefsManager(context)
            if (prefsCheck.isSettingsLocked) {
                Toast.makeText(context, "Settings are locked for ${formatRemainingTime(prefsCheck.lockUntil - System.currentTimeMillis())}", Toast.LENGTH_LONG).show()
                return@DashboardContent
            }
            blockShorts = newVal
            prefs.isBlockShorts = newVal
        },
        onLockClick = { if (!isLocked) showLockDialog = true },
        onLockdownVpnClick = { showAlwaysOnDialog = true },
        onNavigateToUsageStats = onNavigateToUsageStats,
        usageViewModel = usageViewModel
    )

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
                    val ntpTime = com.digitalmonk.app.core.utils.NtpFetcher.fetchNtpTime()
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

@Composable
fun DashboardContent(
    safeSearchEnabled: Boolean,
    blockShorts: Boolean,
    isLocked: Boolean,
    onSafeSearchToggle: (Boolean) -> Unit,
    onBlockShortsToggle: (Boolean) -> Unit,
    onLockClick: () -> Unit,
    onLockdownVpnClick: () -> Unit,
    onNavigateToUsageStats: () -> Unit,
    usageViewModel: UsageViewModel? = null,
    // Preview-only parameters
    previewUsageStats: List<AppUsageInfo>? = null,
    previewComparisonPercent: Int = 0
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        BgDeep,
                        Color(0xFF080B1A), // Deep subtle tint
                        BgDeep
                    )
                )
            )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp, top = 0.dp)
        ) {
            DashboardTabs()

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (usageViewModel != null) {
                    UsageStatsSection(
                        viewModel = usageViewModel,
                        onOpenAllStats = onNavigateToUsageStats
                    )
                } else if (previewUsageStats != null) {
                    UsageStatsSection(
                        usageStats = previewUsageStats,
                        comparisonPercent = previewComparisonPercent,
                        isPermissionGranted = true,
                        onOpenAllStats = onNavigateToUsageStats,
                        onGrantPermission = {}
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                SectionLabel("Content Filters")
                Spacer(modifier = Modifier.height(8.dp))

                DashboardToggleCard(
                    title = "Block Short Videos",
                    description = "Blocks YouTube Shorts, Instagram Reels, TikTok",
                    emoji = "📵",
                    isEnabled = blockShorts,
                    onToggle = onBlockShortsToggle
                )

                Spacer(modifier = Modifier.height(12.dp))

                DashboardToggleCard(
                    title = "SafeSearch & Web Filter",
                    description = "Forces SafeSearch on Google/YouTube & blocks adult sites",
                    emoji = "🛡️",
                    isEnabled = safeSearchEnabled,
                    onToggle = onSafeSearchToggle
                )

                Spacer(modifier = Modifier.height(24.dp))

                DashboardActionCard(
                    title = if (isLocked) "🔒 Settings Locked" else "Lock Settings",
                    description = if (isLocked) "Settings are currently protected" else "Prevent disabling protections for a set period",
                    emoji = "⏳",
                    onClick = onLockClick
                )

                Spacer(modifier = Modifier.height(24.dp))

                DashboardActionCard(
                    title = "Lockdown VPN (Prevent Bypass)",
                    description = "Make the filter permanent so it can't be disabled",
                    emoji = "🔒",
                    onClick = onLockdownVpnClick
                )

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun DashboardTabs() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Daily", "Weekly", "Monthly")
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .padding(5.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val pillWidth = maxWidth / 3
            val pillOffset by animateDpAsState(
                targetValue = pillWidth * selectedTab,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                label = "pill"
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(pillOffset.roundToPx(), 0) }
                    .width(pillWidth)
                    .fillMaxHeight()
                    .padding(1.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF6096FF).copy(alpha = 0.55f),
                                Color(0xFF6096FF).copy(alpha = 0.28f)
                            )
                        )
                    )
                    .border(1.dp, Color(0xFF96BEFF).copy(alpha = 0.55f), RoundedCornerShape(13.dp))
            )
        }
        
        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { selectedTab = index },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) Color(0xFFeaf1ff) else TextSecond,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardToggleCard(
    title: String, description: String, emoji: String,
    isEnabled: Boolean, onToggle: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
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
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
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

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    val mockUsageData = listOf(
        AppUsageInfo("com.google.android.youtube", "YouTube", null, 120 * 60 * 1000L, 10),
        AppUsageInfo("com.instagram.android", "Instagram", null, 45 * 60 * 1000L, 5),
        AppUsageInfo("com.zhiliaoapp.musically", "TikTok", null, 30 * 60 * 1000L, 8)
    )

    DashboardContent(
        safeSearchEnabled = true,
        blockShorts = false,
        isLocked = false,
        onSafeSearchToggle = {},
        onBlockShortsToggle = {},
        onLockClick = {},
        onLockdownVpnClick = {},
        onNavigateToUsageStats = {},
        previewUsageStats = mockUsageData,
        previewComparisonPercent = -12
    )
}
