package com.example.digitalmonk.ui.dashboard

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.ComponentName
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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val hasNotification: Boolean
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
            DigitalMonkTheme {
                AppContent(prefs)
            }
        }
    }

    private fun getPermissionsState(context: android.content.Context): PermissionsState {
        return PermissionsState(
            isAccessibilityOn = PermissionHelper.isAccessibilityEnabled(context),
            isBatteryExempt = PersistenceManager.isBatteryOptimizationDisabled(context),
            canDrawOverlays = PersistenceManager.canDrawOverlays(context),
            isDeviceAdmin = MonkDeviceAdminReceiver.isAdminActive(context),
            hasUsageStats = PersistenceManager.hasUsageStatsPermission(context),
            hasNotification = PermissionHelper.hasNotificationPermission(context)
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
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
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

        // ── Single source of truth for refresh ───────────────────────────────
        var refreshKey by remember { mutableLongStateOf(0L) }

        // ── Permission state declared as var so LaunchedEffect can update it ─
        var permissionsState by remember { mutableStateOf(getPermissionsState(context)) }

        // ── Polling effect — re-runs every time refreshKey changes (ON_RESUME) ─
        // Checks 3 times with 500ms gaps to defeat Android's PowerManager
        // cache delay that causes isBatteryOptimizationDisabled() to return a
        // stale false value immediately after the user taps "Allow".
        LaunchedEffect(refreshKey) {
            permissionsState = getPermissionsState(context)
            kotlinx.coroutines.delay(500)
            permissionsState = getPermissionsState(context)
            kotlinx.coroutines.delay(500)
            permissionsState = getPermissionsState(context)
        }

        // ── Lifecycle observer — bumps refreshKey on every ON_RESUME ─────────
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refreshKey = System.currentTimeMillis()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val scrimAlpha by animateFloatAsState(
            targetValue = if (sidebarOpen) 0.6f else 0f,
            animationSpec = tween(300),
            label = "scrim"
        )

        Box(modifier = Modifier.fillMaxSize().background(BgDeep)) {
            // ── Main content ─────────────────────────────────────────────────
            DashboardContent(
                prefs = prefs,
                permissionsState = permissionsState,
                onRefresh = { refreshKey = System.currentTimeMillis() },
                onLock = onLock,
                onMenuClick = { sidebarOpen = true }
            )

            // ── Scrim ────────────────────────────────────────────────────────
            if (scrimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(scrimAlpha)
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures { sidebarOpen = false }
                        }
                )
            }

            // ── Sidebar ──────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = sidebarOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it })
            ) {
                PermissionsSidebar(
                    permissionsState = permissionsState,
                    onRefresh = { refreshKey = System.currentTimeMillis() },
                    onClose = { sidebarOpen = false }
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permissions Sidebar
    // ─────────────────────────────────────────────────────────────────────────

    @Composable
    fun PermissionsSidebar(
        permissionsState: PermissionsState,
        onRefresh: () -> Unit,
        onClose: () -> Unit
    ) {
        val context = LocalContext.current

        // No lifecycle observer here — the parent Dashboard owns the refresh cycle.

        val deviceAdminLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { onRefresh() }

        val batteryLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            // Do NOT use SharedPreferences flags — just trigger the polling refresh.
            onRefresh()
        }

        val grantedCount = listOf(
            permissionsState.isAccessibilityOn,
            permissionsState.isBatteryExempt,
            permissionsState.canDrawOverlays,
            permissionsState.isDeviceAdmin,
            permissionsState.hasUsageStats,
            permissionsState.hasNotification
        ).count { it }
        val totalCount = 6

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
            // Decorative edge glow
            Canvas(modifier = Modifier.fillMaxHeight().width(3.dp).align(Alignment.CenterEnd)) {
                drawLine(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, SidebarEdge, AccentBlue.copy(alpha = 0.6f), SidebarEdge, Color.Transparent)
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF0F2A4A), SidebarBg)
                            )
                        )
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
                                    Text(
                                        "Permissions",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        "System Access",
                                        fontSize = 11.sp,
                                        color = TextSecond,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                            IconButton(onClick = onClose) {
                                Text("✕", fontSize = 16.sp, color = TextSecond)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // ── Progress bar ──────────────────────────────────────
                        val progress = grantedCount.toFloat() / totalCount.toFloat()
                        val progressColor = when {
                            progress >= 1f -> AccentGreen
                            progress >= 0.5f -> AccentAmber
                            else -> AccentRed
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
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
                                        .background(
                                            Brush.horizontalGradient(listOf(progressColor.copy(0.7f), progressColor))
                                        )
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "$grantedCount / $totalCount",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = progressColor
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

                Spacer(Modifier.height(8.dp))

                // ── Section: Critical ──────────────────────────────────────────
                SidebarSectionLabel("CRITICAL")

                SidebarPermissionRow(
                    emoji = "♿",
                    title = "Accessibility Service",
                    subtitle = "Required for app & Shorts blocking",
                    isGranted = permissionsState.isAccessibilityOn,
                    isCritical = true,
                    onAction = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).also {
                            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                )

                SidebarDivider()

                SidebarPermissionRow(
                    emoji = "🔋",
                    title = "Battery Optimization",
                    subtitle = "Keeps app alive in background",
                    isGranted = permissionsState.isBatteryExempt,
                    isCritical = true,
                    onAction = {
                        batteryLauncher.launch(PersistenceManager.buildBatteryOptimizationIntent(context))
                    }
                )

                SidebarDivider()

                SidebarPermissionRow(
                    emoji = "🪟",
                    title = "Display Over Other Apps",
                    subtitle = "Shows block screen on restricted apps",
                    isGranted = permissionsState.canDrawOverlays,
                    isCritical = true,
                    onAction = {
                        context.startActivity(PersistenceManager.buildOverlayPermissionIntent(context))
                    }
                )

                Spacer(Modifier.height(16.dp))

                // ── Section: Important ─────────────────────────────────────────
                SidebarSectionLabel("IMPORTANT")

                SidebarPermissionRow(
                    emoji = "🛡️",
                    title = "Device Admin",
                    subtitle = "Prevents app from being uninstalled",
                    isGranted = permissionsState.isDeviceAdmin,
                    isCritical = false,
                    onAction = {
                        deviceAdminLauncher.launch(MonkDeviceAdminReceiver.buildActivationIntent(context))
                    }
                )

                SidebarDivider()

                SidebarPermissionRow(
                    emoji = "📊",
                    title = "Usage Access",
                    subtitle = "Tracks screen time per app",
                    isGranted = permissionsState.hasUsageStats,
                    isCritical = false,
                    onAction = {
                        context.startActivity(PersistenceManager.buildUsageStatsIntent())
                    }
                )

                SidebarDivider()

                SidebarPermissionRow(
                    emoji = "🔔",
                    title = "Notifications",
                    subtitle = "Alerts when content is blocked",
                    isGranted = permissionsState.hasNotification,
                    isCritical = false,
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

                // ── Refresh hint ───────────────────────────────────────────────
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
                        fontSize = 11.sp,
                        color = TextSecond,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }

    @Composable
    private fun SidebarSectionLabel(label: String) {
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
    private fun SidebarDivider() {
        HorizontalDivider(
            color = Divider,
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }

    @Composable
    private fun SidebarPermissionRow(
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
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = TextSecond
                )
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
                    Text(
                        "Fix →",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = chipColor
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main dashboard scrollable content
    // ─────────────────────────────────────────────────────────────────────────

    @Composable
    fun DashboardContent(
        prefs: PrefsManager,
        permissionsState: PermissionsState,
        onRefresh: () -> Unit,
        onLock: () -> Unit,
        onMenuClick: () -> Unit
    ) {
        val context = LocalContext.current
        var safeSearchEnabled by remember { mutableStateOf(prefs.safeSearchEnabled) }
        var showAlwaysOnDialog by remember { mutableStateOf(false) }

        // NOTE: No local refreshKey or lifecycleOwner here.
        // Permission state is owned by Dashboard and passed down via permissionsState.
        // The batteryOptLauncher below just calls onRefresh() to trigger the parent's
        // polling LaunchedEffect — it does NOT read permission state directly.

        val vpnPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                safeSearchEnabled = true
                prefs.safeSearchEnabled = true
                context.startService(Intent(context, DnsVpnService::class.java))
            } else {
                safeSearchEnabled = false
                prefs.safeSearchEnabled = false
                Toast.makeText(context, "VPN Permission is required for Web Filtering", Toast.LENGTH_LONG).show()
            }
        }

        val batteryOptLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { onRefresh() }

        var blockShorts by remember { mutableStateOf(prefs.blockShorts) }

        val missingCritical = listOf(
            permissionsState.isAccessibilityOn,
            permissionsState.isBatteryExempt,
            permissionsState.canDrawOverlays
        ).count { !it }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDeep)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp, top = 0.dp)
        ) {
            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 52.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = onMenuClick),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(TextPrimary)
                            )
                        }
                    }

                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Digital Monk",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "Parent Dashboard",
                        fontSize = 11.sp,
                        color = TextSecond,
                        letterSpacing = 0.3.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .clickable(onClick = onLock),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔒", fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Permission alert banner ───────────────────────────────────────
            AnimatedVisibility(
                visible = missingCritical > 0,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF3B0A0A), Color(0xFF4A1515))
                            )
                        )
                        .clickable(onClick = onMenuClick)
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$missingCritical critical permission${if (missingCritical > 1) "s" else ""} missing",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentRed
                            )
                            Text(
                                "Tap to open Permissions panel and fix",
                                fontSize = 11.sp,
                                color = Color(0xFFEF9999)
                            )
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
                description = if (permissionsState.isAccessibilityOn) "Active — Monitoring is running"
                else "Disabled — Tap to enable in Settings",
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
                description = if (permissionsState.isBatteryExempt) "Disabled — App can run freely in background"
                else "Active — App may be killed by OEM. Tap to fix",
                isActive = permissionsState.isBatteryExempt,
                onClick = {
                    // Guard: do nothing if already exempt.
                    // Without this guard, tapping the green card would call onRefresh()
                    // via batteryOptLauncher, which triggers the polling LaunchedEffect.
                    // The first poll at t=0ms reads a stale false from the PowerManager
                    // cache, causing the card to flicker red before the 500ms checks
                    // correct it — even though the permission was never changed.
                    if (!permissionsState.isBatteryExempt) {
                        batteryOptLauncher.launch(PersistenceManager.buildBatteryOptimizationIntent(context))
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            StatusCard(
                title = "Display Over Other Apps",
                description = if (permissionsState.canDrawOverlays) "Granted — Block screen can appear"
                else "Missing — Block screen won't show. Tap to fix",
                isActive = permissionsState.canDrawOverlays,
                onClick = {
                    if (!permissionsState.canDrawOverlays) {
                        context.startActivity(PersistenceManager.buildOverlayPermissionIntent(context))
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Content Filters ───────────────────────────────────────────────
            SectionLabel("Content Filters")
            Spacer(modifier = Modifier.height(8.dp))

            ToggleCard(
                title = "Block Short Videos",
                description = "Blocks YouTube Shorts, Instagram Reels, TikTok",
                emoji = "📵",
                isEnabled = blockShorts,
                onToggle = { blockShorts = it; prefs.blockShorts = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ToggleCard(
                title = "SafeSearch & Web Filter",
                description = "Forces SafeSearch on Google/YouTube & blocks adult sites",
                emoji = "🛡️",
                isEnabled = safeSearchEnabled,
                onToggle = { isChecked ->
                    if (isChecked) {
                        val vpnIntent = VpnService.prepare(context)
                        if (vpnIntent != null) {
                            vpnPermissionLauncher.launch(vpnIntent)
                        } else {
                            safeSearchEnabled = true
                            prefs.safeSearchEnabled = true
                            context.startService(Intent(context, DnsVpnService::class.java))
                        }
                    } else {
                        safeSearchEnabled = false
                        prefs.safeSearchEnabled = false
                        // Must match DnsVpnService.ACTION_STOP exactly — the service
                        // ignores any intent whose action doesn't equal "ACTION_STOP".
                        val stopIntent = Intent(context, DnsVpnService::class.java).apply {
                            action = DnsVpnService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                    }
                }
            )

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
                        context.startActivity(
                            Intent("android.net.vpn.SETTINGS").apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
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
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) Color(0xFF052E16) else BgCard
            ),
            shape = RoundedCornerShape(14.dp),
            // Only register clicks when NOT active. An already-green card must never
            // trigger onClick — doing so would fire onRefresh() → LaunchedEffect →
            // first poll reads stale false from PowerManager cache → card flickers red.
            modifier = if (!isActive) Modifier.fillMaxWidth().clickable(onClick = onClick)
            else Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isActive) "🟢" else "🔴", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 15.sp)
                    Text(description, color = TextSecond, fontSize = 12.sp)
                }
                if (!isActive) {
                    Text("FIX →", color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    fun ToggleCard(title: String, description: String, emoji: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
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
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentBlue,
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = TextMuted
                    )
                )
            }
        }
    }

    @Composable
    fun ActionCard(title: String, description: String, emoji: String, onClick: () -> Unit) {
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

    private fun isAccessibilityEnabled(context: android.content.Context): Boolean {
        val expectedComponent = ComponentName(context, GuardianAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
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
// AlwaysOnVpnDialog — top-level composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AlwaysOnVpnDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                ) {
                    Text("Open VPN Settings", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Maybe Later", color = Color(0xFF64748B), fontSize = 13.sp)
                }
            }
        }
    }
}