package com.example.digitalmonk.ui.dashboard

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.service.accessibility.GuardianAccessibilityService
import com.example.digitalmonk.ui.auth.PinGateScreen
import com.example.digitalmonk.ui.auth.PinSetupActivity
import com.example.digitalmonk.ui.components.common.SectionLabel
import com.example.digitalmonk.ui.theme.DigitalMonkTheme
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import com.example.digitalmonk.core.utils.PermissionHelper
import android.net.VpnService
import android.app.Activity.RESULT_OK
import com.example.digitalmonk.service.vpn.DnsVpnService
import com.example.digitalmonk.core.utils.PersistenceManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.viewmodel.compose.viewModel



// TODO: Add @AndroidEntryPoint when Hilt is added to build.gradle.kts
class MainActivity : BaseActivity() {

    private val requestNotificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, notifications will work
        } else {
            // Optional: show a message explaining why notifications are needed
        }
    }

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
            if (isUnlocked) {
                askForNotificationPermission()
            }
        }

        if (isUnlocked) {
            Dashboard(prefs, onLock = { isUnlocked = false })
        } else {
            PinGateScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return com.example.digitalmonk.ui.auth.AuthViewModel(prefs) as T
                        }
                    }
                ),
                onSuccess = { isUnlocked = true }
            )
        }
    }

    @Composable
    fun Dashboard(prefs: PrefsManager, onLock: () -> Unit) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        var refreshKey by remember { mutableLongStateOf(0L) }
        var safeSearchEnabled by remember { mutableStateOf(prefs.safeSearchEnabled) }

        // Controls the Always-On VPN setup guide dialog.
        // ONLY set to true by the "Lockdown VPN" button — never by the SafeSearch toggle.
        var showAlwaysOnDialog by remember { mutableStateOf(false) }

        // Launcher for Android's VPN consent dialog.
        // Its only job: ask the OS for permission to use VPN.
        // Starting the service after "Allow" is all it does — no dialog, no extra logic.
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
                Toast.makeText(
                    context,
                    "VPN Permission is required for Web Filtering",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // This effect updates refreshKey every time you come back to the app
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshKey = System.currentTimeMillis()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val isAccessibilityOn = remember(refreshKey) { isAccessibilityEnabled(context) }
        val isBatteryExempt   = remember(refreshKey) { PersistenceManager.isBatteryOptimizationDisabled(context) }
        val canDrawOverlays   = remember(refreshKey) { PersistenceManager.canDrawOverlays(context) }


        val batteryOptLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { refreshKey = System.currentTimeMillis() }

        var blockShorts by remember { mutableStateOf(prefs.blockShorts) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 50.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Digital Monk",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text("Parent Dashboard", fontSize = 13.sp, color = Color(0xFF64748B))
                }
                TextButton(onClick = onLock) {
                    Text("Lock 🔒", color = Color(0xFF64748B))
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Service Status ────────────────────────────────────────────────
            SectionLabel("Service Status")
            Spacer(modifier = Modifier.height(8.dp))

            StatusCard(
                title = "Accessibility Service",
                description = if (isAccessibilityOn)
                    "Active — Monitoring is running"
                else
                    "Disabled — Tap to enable in Settings",
                isActive = isAccessibilityOn,
                onClick = {
                    if (!isAccessibilityOn) {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        Toast.makeText(
                            context,
                            "Find 'Digital Monk' and turn ON",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            StatusCard(
                title = "Battery Optimization",
                description = if (isBatteryExempt) "Disabled — App can run freely in background"
                else "Active — App may be killed by MIUI/OEM. Tap to fix",
                isActive = isBatteryExempt,
                onClick = {
                    if (!isBatteryExempt) {
                        batteryOptLauncher.launch(PersistenceManager.buildBatteryOptimizationIntent(context))
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            StatusCard(
                title = "Display Over Other Apps",
                description = if (canDrawOverlays) "Granted — Block screen can appear"
                else "Missing — Block screen won't show. Tap to fix",
                isActive = canDrawOverlays,
                onClick = {
                    if (!canDrawOverlays) {
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
                onToggle = {
                    blockShorts = it
                    prefs.blockShorts = it
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // SafeSearch toggle: starts/stops the VPN service only.
            // Asks Android for VPN permission if this is the first time.
            // Does NOT show the Always-On dialog — that is a separate concern.
            ToggleCard(
                title = "SafeSearch & Web Filter",
                description = "Forces SafeSearch on Google/YouTube & blocks adult sites",
                emoji = "🛡️",
                isEnabled = safeSearchEnabled,
                onToggle = { isChecked ->
                    if (isChecked) {
                        val vpnIntent = VpnService.prepare(context)
                        if (vpnIntent != null) {
                            // First time — need OS permission
                            vpnPermissionLauncher.launch(vpnIntent)
                        } else {
                            // Already have permission, just start the service
                            safeSearchEnabled = true
                            prefs.safeSearchEnabled = true
                            context.startService(Intent(context, DnsVpnService::class.java))
                        }
                    } else {
                        safeSearchEnabled = false
                        prefs.safeSearchEnabled = false
                        val stopIntent = Intent(context, DnsVpnService::class.java).apply {
                            action = "STOP"
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
                onClick = {
                    startActivity(Intent(this@MainActivity, PinSetupActivity::class.java))
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Lockdown button: ONLY place showAlwaysOnDialog is set to true.
            ActionCard(
                title = "Lockdown VPN (Prevent Bypass)",
                description = "Make the filter permanent so it can't be disabled by the child",
                emoji = "🔒",
                onClick = {
                    showAlwaysOnDialog = true
                }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ── Accessibility warning banner ───────────────────────────────────
            if (!isAccessibilityOn) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A5F)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Text("ℹ️", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "The Accessibility Service must be enabled for blocking to work. " +
                                    "Go to Settings → Accessibility → Digital Monk → Turn ON.",
                            fontSize = 13.sp,
                            color = Color(0xFF93C5FD)
                        )
                    }
                }
            }
        }

        // Rendered OUTSIDE the Column so it floats on top correctly.
        if (showAlwaysOnDialog) {
            AlwaysOnVpnDialog(
                onOpenSettings = {
                    showAlwaysOnDialog = false
                    try {
                        val intent = Intent("android.net.vpn.SETTINGS")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Go to Settings → Network → VPN",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onDismiss = { showAlwaysOnDialog = false }
            )
        }
    }

    // ── Reusable card composables ─────────────────────────────────────────────

    @Composable
    fun StatusCard(
        title: String,
        description: String,
        isActive: Boolean,
        onClick: () -> Unit
    )
    {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) Color(0xFF052E16) else Color(0xFF1C1917)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isActive) "🟢" else "🔴", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                    Text(description, color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
                if (!isActive) {
                    Text("FIX →", color = Color(0xFF3B82F6), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    fun ToggleCard(
        title: String,
        description: String,
        emoji: String,
        isEnabled: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                    Text(description, color = Color(0xFF64748B), fontSize = 12.sp)
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF3B82F6),
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF334155)
                    )
                )
            }
        }
    }

    @Composable
    fun ActionCard(
        title: String,
        description: String,
        emoji: String,
        onClick: () -> Unit
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                    Text(description, color = Color(0xFF64748B), fontSize = 12.sp)
                }
                Text("→", color = Color(0xFF475569), fontSize = 18.sp)
            }
        }
    }

    private fun isAccessibilityEnabled(context: android.content.Context): Boolean {
        val expectedComponent = ComponentName(context, GuardianAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
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

// ── AlwaysOnVpnDialog — top-level composable (outside the class) ──────────────

@Composable
fun AlwaysOnVpnDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
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

                Text(
                    "Make Filter Permanent",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Enable \"Always-On VPN\" so the filter stays active even after a restart and can't be bypassed.",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                val steps = listOf(
                    "1️⃣" to "Tap 'Open VPN Settings' below",
                    "2️⃣" to "Find 'Digital Monk Shield'",
                    "3️⃣" to "Tap the ⚙️ gear icon next to it",
                    "4️⃣" to "Enable 'Always-on VPN'",
                    "5️⃣" to "Optional: Enable 'Block without VPN'"
                )

                steps.forEach { (emoji, text) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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