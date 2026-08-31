package com.curbme.app.core.utils

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.text.TextUtils.SimpleStringSplitter
import androidx.core.content.ContextCompat
import com.curbme.app.receiver.MonkDeviceAdminReceiver
import com.curbme.app.service.accessibility.GuardianAccessibilityService

/**
 * Central system permission utility provider for DigitalMonk.
 * Communicates with the Android platform subsystem to handle security flags,
 * constraint validations, and configuration routing intents.
 */
object PermissionHelper {
    /**
     * Aggregated constraint safety validation check. Returns true only if
     * core platform engine rules are entirely satisfied.
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return isAccessibilityEnabled(context) &&
                canDrawOverlays(context) &&
                hasUsageStatsPermission(context) &&
                isIgnoringBatteryOptimizations(context)
    }

    /* =========================================================================
       ACCESSIBILITY SERVICE
       ========================================================================= */
    /**
     * Accessibility service — required for Shorts blocking & App blocking.
     * Checks if 'GuardianAccessibilityService' is currently active in system settings.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, GuardianAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        if (enabledServices == null) return false

        val splitter = SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val componentString = splitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentString)
            if (expected == enabledComponent) {
                return true
            }
        }
        return false
    }

    /**
     * Direct navigation intent routing to the platform accessibility list structure.
     */
    fun openAccessibilityServiceScreen(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val expectedComponent = ComponentName(context, GuardianAccessibilityService::class.java)

            // Highlight your specific app target package inside submenus where natively supported
            intent.putExtra(":settings:fragment_args_key", expectedComponent.flattenToString())
            val bundle = Bundle()
            bundle.putString(":settings:fragment_args_key", expectedComponent.flattenToString())
            intent.putExtra(":settings:show_fragment_args", bundle)

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // General platform fallback handler action
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /* =========================================================================
       DISPLAY OVER OTHER APPS (SYSTEM OVERLAY)
       ========================================================================= */
    /**
     * SYSTEM_ALERT_WINDOW — required for overlay / block screen.
     */
    fun canDrawOverlays(context: Context?): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Opens the direct system overlay drawer manager page targeting our unique package.
     */
    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.getPackageName())
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /* =========================================================================
       PACKAGE USAGE STATISTICS
       ========================================================================= */
    /**
     * PACKAGE_USAGE_STATS — required for screen time tracking.
     * Uses AppOpsManager to check if the user has allowed the app to see usage data.
     */
    @JvmStatic
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager?
        if (appOps == null) return false

        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.getPackageName()
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Displays the full list configuration for Usage Data authorizations.
     */
    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /* =========================================================================
       BATTERY OPTIMIZATION EXEMPTION
       ========================================================================= */
    /**
     * Verifies if the operating system power manager has whitelisted the package.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName())
    }

    /**
     * Prompts the system dialog directly asking to exempt the app from battery limits.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + context.getPackageName())
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(
                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /* =========================================================================
       SYSTEM NOTIFICATIONS (ANDROID 13+)
       ========================================================================= */
    /**
     * Checks for POST_NOTIFICATIONS permission (Required for Android 13+).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    /**
     * Routing layout intent targeting the unique notifications preference board.
     */
    fun openAppNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /* =========================================================================
       DEVICE ADMINISTRATOR (ANTI-UNINSTALL)
       ========================================================================= */
    /**
     * Determines whether the active policy administration channel is bound.
     */
    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?
        if (dpm == null) return false

        val adminComponent = ComponentName(context, MonkDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    /**
     * Triggers the full system device administration confirmation panel challenge.
     */
    fun openDeviceAdminSettings(context: Context) {
        val adminComponent = ComponentName(context, MonkDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
        intent.putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Activate to prevent CurbMe from being uninstalled without parental permission."
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /* =========================================================================
       VPN CONFIGURATIONS (LOCAL TUNNEL & NATIVE ALWAYS-ON)
       ========================================================================= */
    /**
     * Verifies if local VPN interception plumbing is permitted by the system.
     */
    fun isVpnPermissionGranted(context: Context?): Boolean {
        return VpnService.prepare(context) == null
    }

    /**
     * Native Android validation logic verifying if DigitalMonk is registered
     * as the device's persistent, underlying lockdown provider.
     */
    fun isAlwaysOnVpnActive(context: Context): Boolean {
        val alwaysOnApp = Settings.Secure.getString(
            context.getContentResolver(),
            "always_on_vpn_app"
        )
        return alwaysOnApp != null && alwaysOnApp == context.getPackageName()
    }

    /**
     * Loads the core Android system network configuration VPN list directly.
     */
    fun openVpnSettings(context: Context) {
        val intent = Intent("android.net.vpn.SETTINGS")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /* =========================================================================
       EXACT ALARM OPERATIONS & OEM SPECIFICS
       ========================================================================= */
    /**
     * Confirms runtime availability for high-precision temporal executions (Android 12+).
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
            return am != null && am.canScheduleExactAlarms()
        }
        return true
    }

    /**
     * OEM Target Routing: Force opens MIUI security panel autostart configurations.
     */
    fun openXiaomiAutoStartSettings(context: Context) {
        try {
            val intent = Intent()
            intent.setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (ignored: Exception) {
            // Gracefully ignore on non-Xiaomi setups or unexpected OS variant drops
        }
    }

    fun openXiaomiBackgroundPopupSettings(context: Context) {
        try {
            val intent = Intent()
            intent.setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
            )
            intent.putExtra("extra_pkgname", context.getPackageName())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (ignored: Exception) {
            // Fallback to general security center
            try {
                val fallback = Intent()
                fallback.setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.securitycenter.MainActivity"
                    )
                )
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
            } catch (e: Exception) {
                // Non-Xiaomi, silently ignore
            }
        }
    }

    fun openXiaomiPowerSavingSettings(context: Context) {
        try {
            val intent = Intent()
            intent.setComponent(
                ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
                )
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (ignored: Exception) {
            try {
                val fallback = Intent()
                fallback.setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.securitycenter.MainActivity"
                    )
                )
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
            } catch (e: Exception) {
            }
        }
    }

    fun getLauncherPackageName(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }
}