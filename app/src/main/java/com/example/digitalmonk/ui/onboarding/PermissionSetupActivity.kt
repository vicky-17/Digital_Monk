package com.example.digitalmonk.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.digitalmonk.core.base.BaseActivity
import com.example.digitalmonk.core.utils.PermissionHelper
import com.example.digitalmonk.core.utils.PersistenceManager
import com.example.digitalmonk.receiver.MonkDeviceAdminReceiver
import com.example.digitalmonk.service.WatchdogService
import com.example.digitalmonk.ui.dashboard.MainActivity
import com.example.digitalmonk.ui.theme.DigitalMonkTheme

class PermissionSetupActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DigitalMonkTheme {
                PermissionSetupContent(
                    onComplete = {
                        WatchdogService.start(this)
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun PermissionSetupContent(onComplete: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableLongStateOf(0L) }

    // SharedPrefs for tracking OEM screens the user has visited
    // (We can't programmatically detect if MIUI autostart is ON, so we track "visited")
    val prefs = remember { context.getSharedPreferences("monk_prefs", Context.MODE_PRIVATE) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey = System.currentTimeMillis()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isAccessibilityOn by remember(refreshKey) { mutableStateOf(PermissionHelper.isAccessibilityEnabled(context)) }
    val isBatteryExempt   by remember(refreshKey) { mutableStateOf(PersistenceManager.isBatteryOptimizationDisabled(context)) }
    val canDrawOverlays   by remember(refreshKey) { mutableStateOf(PersistenceManager.canDrawOverlays(context)) }
    val isDeviceAdmin     by remember(refreshKey) { mutableStateOf(MonkDeviceAdminReceiver.isAdminActive(context)) }
    val hasUsageStats     by remember(refreshKey) { mutableStateOf(PersistenceManager.hasUsageStatsPermission(context)) }
    val hasOemAutostart   by remember(refreshKey) { mutableStateOf(PersistenceManager.hasOemAutostartSetting(context)) }
    val isXiaomi          = PersistenceManager.detectOem() == PersistenceManager.OemType.XIAOMI

    // FIX 1: These must be declared at the top of the composable, not inside an `if` block,
    // because Compose requires all remember() calls to be called unconditionally (Rules of Hooks).
    var userVisitedAutostart by remember { mutableStateOf(prefs.getBoolean("visited_autostart", false)) }
    var visitedMiuiPower     by remember { mutableStateOf(prefs.getBoolean("visited_miui_power", false)) }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshKey = System.currentTimeMillis() }

    val batteryOptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Just refresh the UI to check the real Android permission state
        refreshKey = System.currentTimeMillis()
    }

    // FIX 2: allCriticalGranted now references userVisitedAutostart which is declared above
    val allCriticalGranted = isAccessibilityOn && isBatteryExempt && canDrawOverlays &&
            (!hasOemAutostart || userVisitedAutostart) &&
            (!isXiaomi || visitedMiuiPower)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 40.dp)
    ) {
        Text("🔐", fontSize = 48.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Setup Permissions",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            "Grant these permissions so Digital Monk can protect this device 24/7.",
            fontSize = 14.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        SectionHeader("🔴 Required")
        Spacer(modifier = Modifier.height(8.dp))

        PermissionCard(
            emoji = "♿",
            title = "Accessibility Service",
            description = "Allows Digital Monk to detect and block apps and short-form videos.",
            isGranted = isAccessibilityOn,
            isCritical = true,
            onAction = {
                context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            actionLabel = "Open Settings"
        )
        Spacer(modifier = Modifier.height(10.dp))

        PermissionCard(
            emoji = "🔋",
            title = "Disable Battery Optimization",
            description = "Prevents Android from killing Digital Monk in the background. Required for 24/7 protection.",
            isGranted = isBatteryExempt,
            isCritical = true,
            onAction = {
                batteryOptLauncher.launch(PersistenceManager.buildBatteryOptimizationIntent(context))
            },
            actionLabel = "Disable Now"
        )
        Spacer(modifier = Modifier.height(10.dp))

        PermissionCard(
            emoji = "🪟",
            title = "Display Over Other Apps",
            description = "Allows Digital Monk to show a block screen when a restricted app is opened.",
            isGranted = canDrawOverlays,
            isCritical = true,
            onAction = {
                context.startActivity(PersistenceManager.buildOverlayPermissionIntent(context))
            },
            actionLabel = "Grant Permission"
        )

        // MIUI-specific: second power saver screen (only shown on Xiaomi devices)
        // FIX 3: prefs is now accessible here because it's declared at the top of the composable
        if (isXiaomi) {
            Spacer(modifier = Modifier.height(10.dp))
            val miuiPowerIntent = remember { PersistenceManager.buildMiuiPowerKeeperIntent(context) }
            PermissionCard(
                emoji = "⚡",
                title = "MIUI Power Saving (Xiaomi)",
                description = "MIUI has a second battery manager that kills apps separately from Android's " +
                        "own optimizer. Open it and set Digital Monk to 'No restrictions'.",
                isGranted = visitedMiuiPower,
                isCritical = true,
                onAction = {
                    prefs.edit().putBoolean("visited_miui_power", true).apply()
                    visitedMiuiPower = true
                    if (miuiPowerIntent != null) context.startActivity(miuiPowerIntent)
                },
                actionLabel = "Open MIUI Power Settings"
            )
        }

        // OEM Autostart card — shown on MIUI, ColorOS, EMUI, etc. (when the screen exists)
        if (hasOemAutostart) {
            Spacer(modifier = Modifier.height(10.dp))
            val oemIntent = remember { PersistenceManager.buildAutostartIntent(context) }
            PermissionCard(
                emoji = "🔁",
                title = "Autostart (${Build.MANUFACTURER})",
                description = "On ${Build.MANUFACTURER} devices, you must manually whitelist this app " +
                        "to allow it to start on boot.\n\n${PersistenceManager.getAutostartInstructions()}",
                isGranted = userVisitedAutostart,
                isCritical = true,
                onAction = {
                    prefs.edit().putBoolean("visited_autostart", true).apply()
                    userVisitedAutostart = true
                    if (oemIntent != null) context.startActivity(oemIntent)
                },
                actionLabel = "Open Autostart"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader("🟡 Important")
        Spacer(modifier = Modifier.height(8.dp))

        PermissionCard(
            emoji = "🛡️",
            title = "Device Admin (Anti-Uninstall)",
            description = "Prevents children from uninstalling Digital Monk. A parent PIN will be required to remove this protection.",
            isGranted = isDeviceAdmin,
            isCritical = false,
            onAction = {
                deviceAdminLauncher.launch(MonkDeviceAdminReceiver.buildActivationIntent(context))
            },
            actionLabel = "Activate"
        )
        Spacer(modifier = Modifier.height(10.dp))

        PermissionCard(
            emoji = "📊",
            title = "Usage Access",
            description = "Allows Digital Monk to track screen time per app for reports and limits.",
            isGranted = hasUsageStats,
            isCritical = false,
            onAction = {
                context.startActivity(PersistenceManager.buildUsageStatsIntent())
            },
            actionLabel = "Grant Access"
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allCriticalGranted) Color(0xFF3B82F6) else Color(0xFF334155)
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                if (allCriticalGranted) "Continue to Dashboard →" else "Skip for now →",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        if (!allCriticalGranted) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "⚠️ Critical permissions are missing. Digital Monk will not work properly until they are granted.",
                fontSize = 12.sp,
                color = Color(0xFFFB923C),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF475569),
        letterSpacing = 1.sp
    )
}

@Composable
private fun PermissionCard(
    emoji: String,
    title: String,
    description: String,
    isGranted: Boolean,
    isCritical: Boolean,
    onAction: () -> Unit,
    actionLabel: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color(0xFF0D2B1A) else Color(0xFF1E293B)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 22.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isGranted) Color(0xFF16A34A)
                            else if (isCritical) Color(0xFFDC2626)
                            else Color(0xFF92400E)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (isGranted) "✓ Granted" else if (isCritical) "Required" else "Optional",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                description,
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 18.sp
            )

            if (!isGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCritical) Color(0xFF3B82F6) else Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(actionLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}