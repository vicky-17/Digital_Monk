package com.example.digitalmonk.core.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * PersistenceManager — handles all "stay alive" concerns.
 *
 * Covers:
 *  1. Battery optimization exemption
 *  2. OEM-specific autostart settings (MIUI, ColorOS, EMUI, OneUI, etc.)
 *  3. Display over other apps permission
 *  4. Detecting which OEM we're on to show the right instructions
 */
object PersistenceManager {

    private const val TAG = "PersistenceManager"

    // ── Battery Optimization ──────────────────────────────────────────────────

    /**
     * Returns true if our app is already exempt from battery optimization.
     * If false, we must request exemption — otherwise Doze mode will kill our services.
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
        Log.d("PersistenceManager", "Battery optimization check: $isIgnoring")
        return isIgnoring
    }

    /**
     * Returns an Intent that opens the battery optimization exemption screen for our app.
     * The user just needs to tap "Allow" / "Don't optimize".
     */
    fun buildBatteryOptimizationIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    // ── Display Over Other Apps ───────────────────────────────────────────────

    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun buildOverlayPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    }

    // ── Usage Stats ───────────────────────────────────────────────────────────

    fun hasUsageStatsPermission(context: Context): Boolean =
        PermissionHelper.hasUsageStatsPermission(context)

    fun buildUsageStatsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    // ── OEM Autostart ─────────────────────────────────────────────────────────

    /**
     * Detects which OEM the device is from.
     * Each OEM has its own hidden settings screen for "Autostart" / background apps.
     */
    enum class OemType {
        XIAOMI,     // MIUI
        OPPO,       // ColorOS
        VIVO,       // FunTouchOS
        HUAWEI,     // EMUI / HarmonyOS
        SAMSUNG,    // OneUI
        ONEPLUS,    // OxygenOS
        ASUS,       // ZenUI
        GENERIC
    }

    fun detectOem(): OemType {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> OemType.XIAOMI
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> OemType.OPPO
            manufacturer.contains("vivo") -> OemType.VIVO
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> OemType.HUAWEI
            manufacturer.contains("samsung") -> OemType.SAMSUNG
            manufacturer.contains("oneplus") -> OemType.ONEPLUS
            manufacturer.contains("asus") -> OemType.ASUS
            else -> OemType.GENERIC
        }
    }

    /**
     * Returns an Intent to open the OEM-specific autostart settings,
     * or null if the device doesn't have one (stock Android).
     */
    fun buildAutostartIntent(context: Context): Intent? {
        val intents = when (detectOem()) {
            OemType.XIAOMI -> listOf(
                Intent().setComponent(ComponentNameCompat("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent().setComponent(ComponentNameCompat("com.miui.securitycenter", "com.miui.securitycenter.MainActivity"))
            )
            OemType.OPPO -> listOf(
                Intent().setComponent(ComponentNameCompat("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.FakeActivity")),
                Intent().setComponent(ComponentNameCompat("com.oppo.safe", "com.oppo.safe.permission.startup.FakeActivity")),
                Intent().setComponent(ComponentNameCompat("com.coloros.oppoguardelf", "com.coloros.powermanager.powersave.PowerUsageModelActivity"))
            )
            OemType.VIVO -> listOf(
                Intent().setComponent(ComponentNameCompat("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                Intent().setComponent(ComponentNameCompat("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))
            )
            OemType.HUAWEI -> listOf(
                Intent().setComponent(ComponentNameCompat("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                Intent().setComponent(ComponentNameCompat("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
            )
            OemType.SAMSUNG -> listOf(
                Intent().setComponent(ComponentNameCompat("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"))
            )
            OemType.ONEPLUS -> listOf(
                Intent().setComponent(ComponentNameCompat("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"))
            )
            OemType.ASUS -> listOf(
                Intent().setComponent(ComponentNameCompat("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutostartActivity"))
            )
            OemType.GENERIC -> emptyList()
        }

        // Try each intent and return the first one that resolves to an activity
        for (intent in intents) {
            if (isIntentResolvable(context, intent)) {
                return intent
            }
        }

        Log.d(TAG, "No OEM autostart intent found for ${Build.MANUFACTURER}")
        return null
    }

    /**
     * Human-readable instructions for the current OEM's autostart settings.
     */
    fun getAutostartInstructions(): String {
        return when (detectOem()) {
            OemType.XIAOMI -> "Security → Manage Apps → Digital Monk → Autostart → Enable"
            OemType.OPPO   -> "Phone Manager → Privacy Permissions → Startup Manager → Digital Monk → Allow"
            OemType.VIVO   -> "iManager → App Manager → Autostart → Digital Monk → Enable"
            OemType.HUAWEI -> "System Manager → App Launch → Digital Monk → Manage manually → Enable all"
            OemType.SAMSUNG -> "Device Care → Battery → Background Usage Limits → Never Sleeping Apps → Add Digital Monk"
            OemType.ONEPLUS -> "Settings → Battery → Battery Optimization → Digital Monk → Don't Optimize"
            OemType.ASUS   -> "Mobile Manager → Autostart → Digital Monk → Enable"
            OemType.GENERIC -> "Settings → Battery → Battery Optimization → Digital Monk → Don't Optimize"
        }
    }


    // Add this to PersistenceManager.kt
    fun buildMiuiPowerKeeperIntent(context: Context): Intent? {
        if (detectOem() != OemType.XIAOMI) return null
        val intent = Intent().setComponent(
            android.content.ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
            )
        )
        return if (isIntentResolvable(context, intent)) intent else null
    }

    fun hasOemAutostartSetting(context: Context): Boolean {
        return buildAutostartIntent(context) != null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun ComponentNameCompat(pkg: String, cls: String): android.content.ComponentName =
        android.content.ComponentName(pkg, cls)

    private fun isIntentResolvable(context: Context, intent: Intent): Boolean {
        return try {
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(intent, 0)
            }
            resolved != null
        } catch (e: Exception) {
            false
        }
    }
}