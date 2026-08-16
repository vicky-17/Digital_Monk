package com.digitalmonk.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
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
import com.digitalmonk.app.core.base.BaseActivity
import com.digitalmonk.app.core.utils.PermissionHelper
import com.digitalmonk.app.core.utils.PersistenceManager
import com.digitalmonk.app.data.local.prefs.PrefsManager
import com.digitalmonk.app.receiver.MonkDeviceAdminReceiver
import com.digitalmonk.app.ui.auth.AuthViewModel
import com.digitalmonk.app.ui.auth.PinGateScreen
import com.digitalmonk.app.ui.auth.PinSetupActivity
import com.digitalmonk.app.ui.permissions.PermissionsScreen
import com.digitalmonk.app.ui.settings.SettingsScreen
import com.digitalmonk.app.ui.sidebar.PermissionsSidebar
import com.digitalmonk.app.ui.theme.DigitalMonkTheme
import kotlinx.coroutines.delay
import com.digitalmonk.app.ui.dashboard.DashboardScreen
import com.digitalmonk.app.ui.security.SecurityScreen
import com.digitalmonk.app.ui.locks.LocksScreen

// ── Color palette ─────────────────────────────────────────────────────────────
private val BgDeep      = Color(0xFF080E1A)
private val BgCard      = Color(0xFF111827)
private val AccentBlue  = Color(0xFF3B82F6)
private val AccentRed   = Color(0xFFEF4444)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecond  = Color(0xFF64748B)
private val TextMuted   = Color(0xFF334155)

// ── Bottom Navigation Screen Routes ───────────────────────────────────────────
enum class Screen(
    val route: String,
    val title: String,
    val icon: String,
    val contentDescription: String
) {
    DASHBOARD("dashboard", "Dashboard", "📊", "Dashboard"),
    LOCKS("locks", "Locks", "🔒️", "Locks"),
    SECURITY("security", "Security", "🛡️", "WebLock"),
    SETTINGS("settings", "Settings", "⚙️", "Settings")
}

// ── Top-level navigation destinations ─────────────────────────────────────────
// Sealed class instead of a Boolean flag — scales cleanly if you add more
// full-screen destinations later (e.g. AppLockDetail, ScheduleEditor, etc.)
sealed class AppDestination {
    /** The main shell: TopBar + BottomBar + tab content */
    object Main : AppDestination()

    /** Full-screen Permissions screen — no TopBar / BottomBar */
    object Permissions : AppDestination()
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
            isBatteryExempt   = PersistenceManager.isBatteryOptimizationDisabled(context),
            canDrawOverlays   = PersistenceManager.canDrawOverlays(context),
            isDeviceAdmin     = MonkDeviceAdminReceiver.isAdminActive(context),
            hasUsageStats     = PersistenceManager.hasUsageStatsPermission(context),
            hasNotification   = PermissionHelper.hasNotificationPermission(context),
            visitedAutostart  = sharedPrefs.getBoolean("visited_autostart", false),
            visitedMiuiPower  = sharedPrefs.getBoolean("visited_miui_power", false),
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

    // ─────────────────────────────────────────────────────────────────────────
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
    // Main Navigation Shell
    // ─────────────────────────────────────────────────────────────────────────
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainNavigationShell(prefs: PrefsManager, onLock: () -> Unit) {
        var currentScreen  by remember { mutableStateOf(Screen.DASHBOARD) }
        var sidebarOpen    by remember { mutableStateOf(false) }

        // ── Single source of truth for which top-level destination is active ──
        var destination by remember { mutableStateOf<AppDestination>(AppDestination.Main) }

        val context       = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        var refreshKey       by remember { mutableLongStateOf(0L) }
        var permissionsState by remember { mutableStateOf(getPermissionsState(context)) }

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

        // ── System back button / gesture handling ─────────────────────────────
        // Intercept back press when we're NOT on Main, navigate back instead of
        // closing the app. BackHandler is inactive when destination == Main so
        // Android's default back behaviour (exit app) is preserved.
        BackHandler(enabled = destination != AppDestination.Main) {
            destination = AppDestination.Main
        }

        val scrimAlpha by animateFloatAsState(
            targetValue    = if (sidebarOpen) 0.6f else 0f,
            animationSpec  = tween(300),
            label          = "scrim"
        )

        // ── AnimatedContent: only ONE destination in the composition tree ─────
        // slideInHorizontally / slideOutHorizontally gives the standard Android
        // "push forward / pop back" feel. The Scaffold + Sidebar are only
        // composed when destination == Main, so PermissionsScreen gets a clean,
        // lean composition tree — fixes the Realme crash.
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                if (targetState == AppDestination.Permissions) {
                    // Navigating forward → slide in from right, old screen exits left
                    slideInHorizontally(
                        animationSpec  = tween(300),
                        initialOffsetX = { fullWidth -> fullWidth }
                    ) togetherWith slideOutHorizontally(
                        animationSpec = tween(300),
                        targetOffsetX = { fullWidth -> -fullWidth }
                    )
                } else {
                    // Navigating back → slide in from left, old screen exits right
                    slideInHorizontally(
                        animationSpec  = tween(300),
                        initialOffsetX = { fullWidth -> -fullWidth }
                    ) togetherWith slideOutHorizontally(
                        animationSpec = tween(300),
                        targetOffsetX = { fullWidth -> fullWidth }
                    )
                }
            },
            label = "destination_transition"
        ) { currentDestination ->

            when (currentDestination) {

                // ── MAIN shell (Scaffold + sidebar) ───────────────────────────
                AppDestination.Main -> {
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
                                            onClick   = { sidebarOpen = true },
                                            modifier  = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector     = Icons.Rounded.Menu,
                                                contentDescription = "Open Navigation Menu",
                                                tint            = TextPrimary,
                                                modifier        = Modifier.size(32.dp)
                                            )
                                        }
                                    },
                                    actions = {
                                        IconButton(
                                            onClick  = {},
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector     = Icons.Rounded.AccountCircle,
                                                contentDescription = "View Account Profile",
                                                tint            = TextSecond,
                                                modifier        = Modifier.size(32.dp)
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
                                            onClick  = { currentScreen = screen },
                                            label    = { Text(screen.title, fontSize = 11.sp) },
                                            icon     = { Text(screen.icon, fontSize = 20.sp) },
                                            colors   = NavigationBarItemDefaults.colors(
                                                selectedIconColor   = AccentBlue,
                                                selectedTextColor   = TextPrimary,
                                                unselectedIconColor = TextSecond,
                                                unselectedTextColor = TextSecond,
                                                indicatorColor      = BgDeep
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
                                when (currentScreen) {
                                    Screen.DASHBOARD -> DashboardScreen(
                                        prefs           = prefs,
                                        refreshKey      = refreshKey,
                                        onRefresh       = { refreshKey = System.currentTimeMillis() },
                                        onChangePinClick = {
                                            startActivity(Intent(this@MainActivity, PinSetupActivity::class.java))
                                        }
                                    )
                                    Screen.LOCKS    -> LocksScreen(prefs = prefs)
                                    Screen.SECURITY -> SecurityScreen(prefs = prefs)
                                    Screen.SETTINGS -> SettingsScreen(
                                        // Flip destination → Compose discards the Scaffold tree
                                        // and only renders PermissionsScreen in its place
                                        onNavigateToPermissions = {
                                            destination = AppDestination.Permissions
                                        }
                                    )
                                }
                            }
                        }

                        // Scrim — rendered above Scaffold, below sidebar
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

                        // Sidebar — slides in from left, globally accessible
                        AnimatedVisibility(
                            visible = sidebarOpen,
                            enter   = slideInHorizontally(initialOffsetX = { -it }),
                            exit    = slideOutHorizontally(targetOffsetX = { -it })
                        ) {
                            PermissionsSidebar(
                                prefs            = prefs,
                                permissionsState = permissionsState,
                                onRefresh        = { refreshKey = System.currentTimeMillis() },
                                onClose          = { sidebarOpen = false }
                            )
                        }
                    }
                }

                // ── PERMISSIONS full-screen (no TopBar / BottomBar) ───────────
                // Only this composable is in the tree — Scaffold is fully gone.
                AppDestination.Permissions -> {
                    PermissionsScreen(
                        onBackClick = { destination = AppDestination.Main }
                    )
                }
            }
        }
    }

    @Composable
    fun FullScreenPlaceholder(label: String) {
        Box(
            modifier        = Modifier.fillMaxSize().background(BgDeep),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = label,
                color      = TextSecond,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}