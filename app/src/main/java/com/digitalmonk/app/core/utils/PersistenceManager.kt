package com.digitalmonk.app.core.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.util.Locale

/**
 * Why we made this file:
 * Parental control apps like Digital Monk must run 24/7 to effectively monitor
 * app usage and block content. Modern Android systems and specific manufacturers
 * (OEMs like Xiaomi or Samsung) have aggressive "Battery Optimizers" that kill
 * background apps. This class provides the logic to detect these systems and
 * guide the user to whitelist the app.
 * 
 * What the file name defines:
 * "Persistence" refers to the ability of the software to stay running.
 * "Manager" identifies it as the central controller for stability tasks.
 */
object PersistenceManager {
    private const val TAG = "PersistenceManager"

    // ── Battery Optimization ──────────────────────────────────────────────────
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
            // FIXED: context.packageName() -> context.getPackageName()
            return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName())
        }
        return true
    }

    fun buildBatteryOptimizationIntent(context: Context): Intent {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        // FIXED: context.packageName() -> context.getPackageName()
        intent.setData(Uri.parse("package:" + context.getPackageName()))
        return intent
    }

    // ── Display Over Other Apps ───────────────────────────────────────────────
    fun canDrawOverlays(context: Context?): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun buildOverlayPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,  // FIXED: context.packageName() -> context.getPackageName()
            Uri.parse("package:" + context.getPackageName())
        )
    }

    // ── Usage Stats ───────────────────────────────────────────────────────────
    fun hasUsageStatsPermission(context: Context): Boolean {
        return PermissionHelper.hasUsageStatsPermission(context)
    }

    fun buildUsageStatsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    // ── OEM Autostart Logic ───────────────────────────────────────────────────
    fun detectOem(): OemType {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains(
                "poco"
            )
        ) return OemType.XIAOMI
        if (manufacturer.contains("oppo") || manufacturer.contains("realme")) return OemType.OPPO
        if (manufacturer.contains("vivo")) return OemType.VIVO
        if (manufacturer.contains("huawei") || manufacturer.contains("honor")) return OemType.HUAWEI
        if (manufacturer.contains("samsung")) return OemType.SAMSUNG
        if (manufacturer.contains("oneplus")) return OemType.ONEPLUS
        if (manufacturer.contains("asus")) return OemType.ASUS
        return OemType.GENERIC
    }

    fun buildAutostartIntent(context: Context): Intent? {
        val intents: MutableList<Intent> = ArrayList<Intent>()
        val oem = detectOem()

        when (oem) {
            OemType.XIAOMI -> {
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    )
                )
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.securitycenter.MainActivity"
                        )
                    )
                )
            }

            OemType.OPPO -> {
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.FakeActivity"
                        )
                    )
                )
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.oppo.safe",
                            "com.oppo.safe.permission.startup.FakeActivity"
                        )
                    )
                )
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.oppoguardelf",
                            "com.coloros.powermanager.powersave.PowerUsageModelActivity"
                        )
                    )
                )
            }

            OemType.VIVO -> {
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    )
                )
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                        )
                    )
                )
            }

            OemType.HUAWEI -> {
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    )
                )
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                        )
                    )
                )
            }

            OemType.SAMSUNG -> intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                    )
                )
            )

            OemType.ONEPLUS -> intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                )
            )

            OemType.ASUS -> intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.asus.mobilemanager",
                        "com.asus.mobilemanager.autostart.AutostartActivity"
                    )
                )
            )

            else -> {}
        }

        for (intent in intents) {
            if (isIntentResolvable(context, intent)) {
                return intent
            }
        }
        return null
    }

    val autostartInstructions: String
        get() {
            when (detectOem()) {
                OemType.XIAOMI -> return "Security → Manage Apps → Digital Monk → Autostart → Enable"
                OemType.OPPO -> return "Phone Manager → Privacy Permissions → Startup Manager → Digital Monk → Allow"
                OemType.VIVO -> return "iManager → App Manager → Autostart → Digital Monk → Enable"
                OemType.HUAWEI -> return "System Manager → App Launch → Digital Monk → Manage manually → Enable all"
                OemType.SAMSUNG -> return "Device Care → Battery → Background Usage Limits → Never Sleeping Apps → Add Digital Monk"
                OemType.ONEPLUS -> return "Settings → Battery → Battery Optimization → Digital Monk → Don't Optimize"
                OemType.ASUS -> return "Mobile Manager → Autostart → Digital Monk → Enable"
                else -> return "Settings → Battery → Battery Optimization → Digital Monk → Don't Optimize"
            }
        }

    fun buildMiuiPowerKeeperIntent(context: Context): Intent? {
        if (detectOem() != OemType.XIAOMI) return null
        val intent = Intent().setComponent(
            ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
            )
        )
        return if (isIntentResolvable(context, intent)) intent else null
    }

    fun hasOemAutostartSetting(context: Context): Boolean {
        return buildAutostartIntent(context) != null
    }

    private fun isIntentResolvable(context: Context, intent: Intent): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return context.getPackageManager()
                    .resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0)) != null
            } else {
                return context.getPackageManager().resolveActivity(intent, 0) != null
            }
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Checks if "Display pop-up windows while running in background" is granted.
     * This is a MIUI-specific AppOps permission separate from SYSTEM_ALERT_WINDOW.
     * We can't reliably check it programmatically on all MIUI versions, so we
     * track whether the user has visited the settings page (like autostart).
     */
    fun buildMiuiBackgroundPopupIntent(context: Context): Intent? {
        if (detectOem() != OemType.XIAOMI) return null

        // Try direct deep-link to MIUI "Other Permissions" page for this app
        try {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
            intent.setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            intent.putExtra("extra_pkgname", context.getPackageName())
            if (isIntentResolvable(context, intent)) return intent
        } catch (ignored: Exception) {
        }

        // Fallback: open standard App Info screen
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.getPackageName())
            )
            if (isIntentResolvable(context, intent)) return intent
        } catch (ignored: Exception) {
        }

        return null
    }

    fun getMiuiBackgroundPopupInstructions(context: Context?): String {
        return "App Info → Other Permissions → " +
                "\"Display pop-up windows while running in background\" → Allow"
    }

    enum class OemType {
        XIAOMI, OPPO, VIVO, HUAWEI, SAMSUNG, ONEPLUS, ASUS, GENERIC
    }
}