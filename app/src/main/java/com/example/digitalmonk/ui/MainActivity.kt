package com.example.digitalmonk.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.digitalmonk.ui.auth.AuthViewModel
import com.example.digitalmonk.ui.auth.PinGateScreen
import com.example.digitalmonk.ui.auth.PinSetupActivity
import com.example.digitalmonk.ui.permissions.PermissionsScreen
import com.example.digitalmonk.ui.settings.SettingsScreen
import com.example.digitalmonk.ui.sidebar.PermissionsSidebar
import com.example.digitalmonk.ui.theme.DigitalMonkTheme
import kotlinx.coroutines.delay
import com.example.digitalmonk.ui.dashboard.DashboardScreen

// ── Color palette ─────────────────────────────────────────────────────────────
private val BgDeep       = Color(0xFF080E1A)
private val BgCard       = Color(0xFF111827)
private val AccentBlue   = Color(0xFF3B82F6)
private val AccentRed    = Color(0xFFEF4444)
private val TextPrimary  = Color(0xFFF1F5F9)
private val TextSecond   = Color(0xFF64748B)
private val TextMuted    = Color(0xFF334155)

// ── Bottom Navigation Screen Routes ───────────────────────────────────────────
enum class Screen(val route: String, val title: String, val icon: String, val contentDescription: String) {
    DASHBOARD("dashboard", "Dashboard", "📊","Dashboard"),
    LOCKS("locks", "Locks", "🔒️", "Locks"),
    SECURITY("security", "Security", "🛡️","WebLock"),
    SETTINGS("settings", "Settings", "⚙️","Settings")
}

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
            DigitalMonkTheme {
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

        isUnlocked = true

        if (isUnlocked) {
            MainNavigationShell(prefs, onLock = { isUnlocked = false })
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
    // NEW: Main Navigation Shell (Top Bar + Bottom Bar + Screen Wrapper)
    // ─────────────────────────────────────────────────────────────────────────
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainNavigationShell(prefs: PrefsManager, onLock: () -> Unit) {
        var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
        var sidebarOpen by remember { mutableStateOf(false) }

        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        var refreshKey by remember { mutableLongStateOf(0L) }
        var permissionsState by remember { mutableStateOf(getPermissionsState(context)) }

        var showPermissions by remember { mutableStateOf(false) }

        LaunchedEffect(refreshKey) {
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
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val scrimAlpha by animateFloatAsState(
            targetValue = if (sidebarOpen) 0.6f else 0f,
            animationSpec = tween(300),
            label = "scrim"
        )

        Box(modifier = Modifier.fillMaxSize().background(BgDeep)) {

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "DigitalMonk",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { sidebarOpen = true },
                                modifier = Modifier.size(48.dp) // Ensures a clean touch target area
                            ){
                                Icon(
                                    // Using Icons.Rounded gives a thicker, bolder look than Icons.Default
                                    imageVector = Icons.Rounded.Menu,
                                    contentDescription = "Open Navigation Menu",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {},
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountCircle,
                                    contentDescription = "View Account Profile",
                                    tint = TextSecond,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = BgCard,
                        tonalElevation = 8.dp
                    ) {
                        Screen.entries.forEach { screen ->
                            NavigationBarItem(
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen },
                                label = { Text(screen.title, fontSize = 11.sp) },
                                icon = { Text(screen.icon, fontSize = 20.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AccentBlue,
                                    selectedTextColor = TextPrimary,
                                    unselectedIconColor = TextSecond,
                                    unselectedTextColor = TextSecond,
                                    indicatorColor = BgDeep
                                )
                            )
                        }
                    }
                },
                containerColor = BgDeep
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Dynamically render screens inside full view space configuration
                    when (currentScreen) {
                        Screen.DASHBOARD -> DashboardScreen(
                            prefs = prefs,
                            refreshKey = refreshKey,
                            onRefresh = { refreshKey = System.currentTimeMillis() },
                            onChangePinClick = { startActivity(Intent(this@MainActivity, PinSetupActivity::class.java)) }
                        )
                        Screen.LOCKS -> FullScreenPlaceholder("🛡️ App & Website Locks")
                        Screen.SECURITY -> FullScreenPlaceholder("📈 Security & Activity")
                        Screen.SETTINGS -> SettingsScreen(
                            onNavigateToPermissions = { showPermissions = true }
                        )
                    }
                }
            }

            // Full-screen Permissions overlay — hides TopBar + BottomBar
            if (showPermissions) {
                PermissionsScreen(
                    onBackClick = { showPermissions = false }
                )
            }

            // Sidebar overlays remain globally accessible above application stack
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

    @Composable
    fun FullScreenPlaceholder(label: String) {
        Box(
            modifier = Modifier.fillMaxSize().background(BgDeep),
            contentAlignment = Alignment.Center
        ) {
            Text(text = label, color = TextSecond, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}




