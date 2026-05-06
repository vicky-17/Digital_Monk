package com.example.digitalmonk.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.digitalmonk.core.base.BaseActivity
import com.example.digitalmonk.core.utils.PermissionHelper
import com.example.digitalmonk.core.utils.PersistenceManager
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.receiver.MonkDeviceAdminReceiver
import com.example.digitalmonk.service.accessibility.GuardianAccessibilityService
import com.example.digitalmonk.service.vpn.DnsVpnService
import com.example.digitalmonk.ui.auth.AuthViewModel
import com.example.digitalmonk.ui.auth.PinGateScreen
import com.example.digitalmonk.ui.auth.PinSetupActivity
import com.example.digitalmonk.ui.components.common.SectionLabel
import com.example.digitalmonk.ui.theme.DigitalMonkTheme
import kotlinx.coroutines.delay
import com.example.digitalmonk.ui.sidebar.PermissionsSidebar




// ── Color palette ─────────────────────────────────────────────────────────────
private val BgDeep       = Color(0xFF080E1A)
private val BgCard       = Color(0xFF111827)
private val BgCardAlt    = Color(0xFF0D1520)
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


data class PermissionsState(
    val isAccessibilityOn: Boolean,
    val isBatteryExempt: Boolean,
    val canDrawOverlays: Boolean,
    val isDeviceAdmin: Boolean,
    val hasUsageStats: Boolean,
    val hasNotification: Boolean,
    val visitedAutostart: Boolean,
    val visitedMiuiPower: Boolean,
    val visitedMiuiBgPopup: Boolean
)


class MainActivity : BaseActivity() {

    private val requestNotificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PrefsManager(this)

        if (!prefs.hasPin()) {
            startActivity(Intent(this, PinSetupActivity::class.java))
            finish()
            return
        }

        setContent {
            DigitalMonkTheme {           // 👉👉 My app Theme Name 🦋✨
                AppContent(prefs)
            }
        }
    }

    private fun getPermissionsState(context: Context): PermissionsState {
        val sharedPrefs = context.getSharedPreferences("monk_prefs", MODE_PRIVATE)

        return PermissionsState(
            isAccessibilityOn = PermissionHelper.isAccessibilityEnabled(context),
            isBatteryExempt = PersistenceManager.isBatteryOptimizationDisabled(context),
            canDrawOverlays = PersistenceManager.canDrawOverlays(context),
            isDeviceAdmin = MonkDeviceAdminReceiver.isAdminActive(context),
            hasUsageStats = PersistenceManager.hasUsageStatsPermission(context),
            hasNotification = PermissionHelper.hasNotificationPermission(context),
            visitedAutostart = sharedPrefs.getBoolean("visited_autostart", false),
            visitedMiuiPower = sharedPrefs.getBoolean("visited_miui_power", false),
            visitedMiuiBgPopup = sharedPrefs.getBoolean("visited_miui_bg_popup", false)
        )
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionHelper.hasNotificationPermission(this)) {
                requestNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @Composable
    fun AppContent(prefs: PrefsManager) {
        var isUnlocked by remember { mutableStateOf(false) }

        LaunchedEffect(isUnlocked) {
            if (isUnlocked) askForNotificationPermission()
        }

        if (isUnlocked) {
            Dashboard(prefs, onLock = { isUnlocked = false })
        } else {
            PinGateScreen(
                viewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return AuthViewModel(prefs) as T
                        }
                    }
                ),
                onSuccess = { isUnlocked = true }
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dashboard — root layout with sidebar overlay
    // ─────────────────────────────────────────────────────────────────────────

    @Composable
    fun Dashboard(prefs: PrefsManager, onLock: () -> Unit) {
        var sidebarOpen by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        var refreshKey by remember { mutableLongStateOf(0L) }
        var permissionsState by remember { mutableStateOf(getPermissionsState(context)) }

        LaunchedEffect(refreshKey) {
            permissionsState = getPermissionsState(context)
            delay(500)
            permissionsState = getPermissionsState(context)
            delay(500)
            permissionsState = getPermissionsState(context)
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshKey = System.currentTimeMillis()

                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        val scrimAlpha by animateFloatAsState(
            targetValue = if (sidebarOpen) 0.6f else 0f,
            animationSpec = tween(300),
            label = "scrim"
        )

        Box(modifier = Modifier.fillMaxSize().background(BgDeep)) {
            DashboardContent(
                prefs = prefs,
                permissionsState = permissionsState,
                refreshKey = refreshKey,
                onRefresh = { refreshKey = System.currentTimeMillis() },
                onLock = onLock,
                onMenuClick = { sidebarOpen = true }
            )

            if (scrimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(scrimAlpha)
                        .background(Color.Black)
                        .pointerInput(Unit) { detectTapGestures { sidebarOpen = false } }
                )
            }

            AnimatedVisibility(
                visible = sidebarOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it })
            ) {
                PermissionsSidebar(
                    prefs = prefs,
                    permissionsState = permissionsState,
                    onRefresh = { refreshKey = System.currentTimeMillis() },
                    onClose = { sidebarOpen = false }
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permissions Sidebar — now includes VPN Settings section
    // ─────────────────────────────────────────────────────────────────────────





    // ─────────────────────────────────────────────────────────────────────────
    // Main dashboard scrollable content
    // ─────────────────────────────────────────────────────────────────────────

    @Composable
    fun DashboardContent(
        prefs: PrefsManager,
        permissionsState: PermissionsState,
        refreshKey: Long,
        onRefresh: () -> Unit,
        onLock: () -> Unit,
        onMenuClick: () -> Unit
    ) {
        val context = LocalContext.current
        var safeSearchEnabled by remember { mutableStateOf(prefs.isSafeSearchEnabled) }
        var showAlwaysOnDialog by remember { mutableStateOf(false) }

        val vpnPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                safeSearchEnabled = true
                prefs.setSafeSearchEnabled(true)
                context.startService(Intent(context, DnsVpnService::class.java))
            } else {
                safeSearchEnabled = false
                prefs.setSafeSearchEnabled(false)
                Toast.makeText(context, "VPN Permission is required for Web Filtering", Toast.LENGTH_LONG).show()
            }
        }

        val batteryOptLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { onRefresh() }

        var blockShorts by remember { mutableStateOf(prefs.isBlockShorts) }

        val missingCritical = listOf(
            permissionsState.isAccessibilityOn,
            permissionsState.isBatteryExempt,
            permissionsState.canDrawOverlays
        ).count { !it }

        Column(
            modifier = Modifier
                .fillMaxSize().background(BgDeep).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp, top = 0.dp)
        ) {
            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 52.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(44.dp).clickable(onClick = onMenuClick), contentAlignment = Alignment.Center) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        repeat(3) {
                            Box(modifier = Modifier.width(22.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(TextPrimary))
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Digital Monk", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Parent Dashboard", fontSize = 11.sp, color = TextSecond, letterSpacing = 0.3.sp)
                }

                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(BgCard).clickable(onClick = onLock),
                    contentAlignment = Alignment.Center
                ) { Text("🔒", fontSize = 18.sp) }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Permission alert banner ───────────────────────────────────────
            AnimatedVisibility(visible = missingCritical > 0, enter = fadeIn(tween(400)), exit = fadeOut(tween(300))) {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF3B0A0A), Color(0xFF4A1515))))
                        .clickable(onClick = onMenuClick).padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("$missingCritical critical permission${if (missingCritical > 1) "s" else ""} missing",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AccentRed)
                            Text("Tap to open Permissions panel and fix", fontSize = 11.sp, color = Color(0xFFEF9999))
                        }
                        Text("›", fontSize = 20.sp, color = AccentRed)
                    }
                }
            }

            if (missingCritical > 0) Spacer(modifier = Modifier.height(16.dp))

            // ── Service Status ────────────────────────────────────────────────
            SectionLabel("Service Status")
            Spacer(modifier = Modifier.height(8.dp))

            StatusCard(
                title = "Accessibility Service",
                description = if (permissionsState.isAccessibilityOn) "Active — Monitoring is running" else "Disabled — Tap to enable in Settings",
                isActive = permissionsState.isAccessibilityOn,
                onClick = {
                    if (!permissionsState.isAccessibilityOn) {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        Toast.makeText(context, "Find 'Digital Monk' and turn ON", Toast.LENGTH_LONG).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            StatusCard(
                title = "Battery Optimization",
                description = if (permissionsState.isBatteryExempt) "Disabled — App can run freely in background" else "Active — App may be killed by OEM. Tap to fix",
                isActive = permissionsState.isBatteryExempt,
                onClick = {
                    if (!permissionsState.isBatteryExempt) {
                        batteryOptLauncher.launch(PersistenceManager.buildBatteryOptimizationIntent(context))
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            StatusCard(
                title = "Display Over Other Apps",
                description = if (permissionsState.canDrawOverlays) "Granted — Block screen can appear" else "Missing — Block screen won't show. Tap to fix",
                isActive = permissionsState.canDrawOverlays,
                onClick = {
                    if (!permissionsState.canDrawOverlays) {
                        context.startActivity(PersistenceManager.buildOverlayPermissionIntent(context))
                    }
                }
            )



            // ── NEW OEM Autostart Logic Start ──────────────────────────
            val hasOemAutostart = remember { PersistenceManager.hasOemAutostartSetting(context) }
            if (hasOemAutostart) {
                Spacer(modifier = Modifier.height(10.dp))
                val oemIntent = remember { PersistenceManager.buildAutostartIntent(context) }
                val prefs2 = remember { context.getSharedPreferences("monk_prefs", MODE_PRIVATE) }

                StatusCard(
                    title = "Background Autostart (${Build.MANUFACTURER})",
                    description = if (permissionsState.visitedAutostart)
                        "Visited — make sure Digital Monk is toggled ON"
                    else
                        "Required on ${Build.MANUFACTURER} — prevents app from being killed",
                    isActive = permissionsState.visitedAutostart,
                    onClick = {
                        prefs2.edit().putBoolean("visited_autostart", true).apply()
                        onRefresh()
                        oemIntent?.let { context.startActivity(it) }
                    }
                )
            }

            val isXiaomi = remember { PersistenceManager.detectOem() == PersistenceManager.OemType.XIAOMI }
            if (isXiaomi) {
                val miuiPowerIntent = remember { PersistenceManager.buildMiuiPowerKeeperIntent(context) }
                if (miuiPowerIntent != null) {
                    val prefs2 = remember { context.getSharedPreferences("monk_prefs", MODE_PRIVATE) }
                    Spacer(modifier = Modifier.height(10.dp))
                    StatusCard(
                        title = "MIUI Power Saver",
                        description = if (permissionsState.visitedMiuiPower)
                            "Visited — ensure set to 'No Restrictions'"
                        else
                            "Second battery manager — must whitelist app here",
                        isActive = permissionsState.visitedMiuiPower,
                        onClick = {
                            prefs2.edit().putBoolean("visited_miui_power", true).apply()
                            onRefresh()
                            context.startActivity(miuiPowerIntent)
                        }
                    )
                }
            }
            // ── NEW OEM Autostart Logic End ────────────────────────────




            Spacer(modifier = Modifier.height(24.dp))

            // ── Content Filters ───────────────────────────────────────────────
            SectionLabel("Content Filters")
            Spacer(modifier = Modifier.height(8.dp))

            ToggleCard(
                title = "Block Short Videos",
                description = "Blocks YouTube Shorts, Instagram Reels, TikTok",
                emoji = "📵",
                isEnabled = blockShorts,
                onToggle = { newVal ->
                    val prefsCheck = PrefsManager(context)
                    if (prefsCheck.isSettingsLocked) {
                        Toast.makeText(
                            context,
                            "Settings are locked for ${formatRemainingTime(prefsCheck.lockUntil - System.currentTimeMillis())}",
                            Toast.LENGTH_LONG
                        ).show()
                        return@ToggleCard
                    }
                    blockShorts = newVal
                    prefs.setBlockShorts(newVal)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ToggleCard(
                title = "SafeSearch & Web Filter",
                description = "Forces SafeSearch on Google/YouTube & blocks adult sites",
                emoji = "🛡️",
                isEnabled = safeSearchEnabled,
                onToggle = { isChecked ->
                    val prefsCheck = PrefsManager(context)
                    if (prefsCheck.isSettingsLocked) {
                        Toast.makeText(
                            context,
                            "Settings are locked for ${formatRemainingTime(prefsCheck.lockUntil - System.currentTimeMillis())}",
                            Toast.LENGTH_LONG
                        ).show()
                        return@ToggleCard
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
                        val stopIntent = Intent(context, DnsVpnService::class.java).apply {
                            action = DnsVpnService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                    }
                }
            )


            Spacer(modifier = Modifier.height(24.dp))

            var showLockDialog by remember { mutableStateOf(false) }
            val isLocked = remember(refreshKey) { PrefsManager(context).isSettingsLocked }

            ActionCard(
                title = if (isLocked) "🔒 Settings Locked" else "Lock Settings",
                description = if (isLocked) {
                    val remaining = PrefsManager(context).lockUntil - System.currentTimeMillis()
                    "Unlocks in ${formatRemainingTime(remaining)}"
                } else {
                    "Prevent disabling protections for a set period"
                },
                emoji = "⏳",
                onClick = { if (!isLocked) showLockDialog = true }
            )

            if (showLockDialog) {
                LockSettingsDialog(
                    onConfirm = { durationMs ->
                        val until = System.currentTimeMillis() + durationMs
                        PrefsManager(context).setLockUntil(until)
                        showLockDialog = false
                        onRefresh()
                    },
                    onDismiss = { showLockDialog = false }
                )
            }






            Spacer(modifier = Modifier.height(24.dp))

            // ── Security ──────────────────────────────────────────────────────
            SectionLabel("Security")
            Spacer(modifier = Modifier.height(8.dp))

            ActionCard(
                title = "Change PIN",
                description = "Update your parent access PIN",
                emoji = "🔑",
                onClick = { startActivity(Intent(this@MainActivity, PinSetupActivity::class.java)) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ActionCard(
                title = "Lockdown VPN (Prevent Bypass)",
                description = "Make the filter permanent so it can't be disabled",
                emoji = "🔒",
                onClick = { showAlwaysOnDialog = true }
            )

            Spacer(modifier = Modifier.height(40.dp))
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

    // ─────────────────────────────────────────────────────────────────────────
    // Reusable card composables
    // ─────────────────────────────────────────────────────────────────────────

    @Composable
    fun StatusCard(title: String, description: String, isActive: Boolean, onClick: () -> Unit) {
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isActive) Color(0xFF052E16) else BgCard),
            shape = RoundedCornerShape(14.dp),
            modifier = if (!isActive) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isActive) "🟢" else "🔴", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 15.sp)
                    Text(description, color = TextSecond, fontSize = 12.sp)
                }
                if (!isActive) Text("FIX →", color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    fun ToggleCard(title: String, description: String, emoji: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = BgCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 15.sp)
                    Text(description, color = TextSecond, fontSize = 12.sp)
                }
                Switch(checked = isEnabled, onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White, checkedTrackColor = AccentBlue,
                        uncheckedThumbColor = Color(0xFF94A3B8), uncheckedTrackColor = TextMuted
                    )
                )
            }
        }
    }

    @Composable
    fun ActionCard(title: String, description: String, emoji: String, onClick: () -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = BgCard), shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, GuardianAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val comp = ComponentName.unflattenFromString(splitter.next())
            if (comp == expectedComponent) return true
        }
        return false
    }
}







// ─────────────────────────────────────────────────────────────────────────────
// AlwaysOnVpnDialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AlwaysOnVpnDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🛡️", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Make Filter Permanent", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Enable \"Always-On VPN\" so the filter stays active even after a restart and can't be bypassed.",
                    fontSize = 14.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(20.dp))
                listOf(
                    "1️⃣" to "Tap 'Open VPN Settings' below",
                    "2️⃣" to "Find 'Digital Monk Shield'",
                    "3️⃣" to "Tap the ⚙️ gear icon next to it",
                    "4️⃣" to "Enable 'Always-on VPN'",
                    "5️⃣" to "Optional: Enable 'Block without VPN'"
                ).forEach { (emoji, text) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
fun LockSettingsDialog(onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
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
                    Text("Set duration. You will NOT be able to disable any protection during this time.", fontSize = 13.sp, color = Color(0xFF94A3B8), lineHeight = 18.sp)
                    Spacer(Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = days, onValueChange = { days = it },
                            label = { Text("Days", color = Color(0xFF64748B)) },
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
                        OutlinedTextField(
                            value = hours, onValueChange = { hours = it },
                            label = { Text("Hours", color = Color(0xFF64748B)) },
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
                        OutlinedTextField(
                            value = minutes, onValueChange = { minutes = it },
                            label = { Text("Mins", color = Color(0xFF64748B)) },
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




