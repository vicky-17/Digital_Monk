package com.digitalmonk.app.core.utils;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.core.content.ContextCompat;

import com.digitalmonk.app.receiver.MonkDeviceAdminReceiver;
import com.digitalmonk.app.service.accessibility.GuardianAccessibilityService;

/**
 * Central system permission utility provider for DigitalMonk.
 * Communicates with the Android platform subsystem to handle security flags,
 * constraint validations, and configuration routing intents.
 */
public class PermissionHelper {

    // Suppress constructor as this is a strict utility class pattern
    private PermissionHelper() {}

    /**
     * Aggregated constraint safety validation check. Returns true only if
     * core platform engine rules are entirely satisfied.
     */
    public static boolean hasAllRequiredPermissions(Context context) {
        return isAccessibilityEnabled(context) &&
                canDrawOverlays(context) &&
                hasUsageStatsPermission(context) &&
                isIgnoringBatteryOptimizations(context);
    }

    /* =========================================================================
       ACCESSIBILITY SERVICE
       ========================================================================= */

    /**
     * Accessibility service — required for Shorts blocking & App blocking.
     * Checks if 'GuardianAccessibilityService' is currently active in system settings.
     */
    public static boolean isAccessibilityEnabled(Context context) {
        ComponentName expected = new ComponentName(context, GuardianAccessibilityService.class);
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

        if (enabledServices == null) return false;

        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            String componentString = splitter.next();
            ComponentName enabledComponent = ComponentName.unflattenFromString(componentString);
            if (expected.equals(enabledComponent)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Direct navigation intent routing to the platform accessibility list structure.
     */
    public static void openAccessibilityServiceScreen(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            ComponentName expectedComponent = new ComponentName(context, GuardianAccessibilityService.class);

            // Highlight your specific app target package inside submenus where natively supported
            intent.putExtra(":settings:fragment_args_key", expectedComponent.flattenToString());
            android.os.Bundle bundle = new android.os.Bundle();
            bundle.putString(":settings:fragment_args_key", expectedComponent.flattenToString());
            intent.putExtra(":settings:show_fragment_args", bundle);

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            // General platform fallback handler action
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /* =========================================================================
       DISPLAY OVER OTHER APPS (SYSTEM OVERLAY)
       ========================================================================= */

    /**
     * SYSTEM_ALERT_WINDOW — required for overlay / block screen.
     */
    public static boolean canDrawOverlays(Context context) {
        return Settings.canDrawOverlays(context);
    }

    /**
     * Opens the direct system overlay drawer manager page targeting our unique package.
     */
    public static void openOverlaySettings(Context context) {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName())
        );
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /* =========================================================================
       PACKAGE USAGE STATISTICS
       ========================================================================= */

    /**
     * PACKAGE_USAGE_STATS — required for screen time tracking.
     * Uses AppOpsManager to check if the user has allowed the app to see usage data.
     */
    public static boolean hasUsageStatsPermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;

        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * Displays the full list configuration for Usage Data authorizations.
     */
    public static void openUsageAccessSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /* =========================================================================
       BATTERY OPTIMIZATION EXEMPTION
       ========================================================================= */

    /**
     * Verifies if the operating system power manager has whitelisted the package.
     */
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    /**
     * Prompts the system dialog directly asking to exempt the app from battery limits.
     */
    public static void openBatteryOptimizationSettings(Context context) {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + context.getPackageName())
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(
                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /* =========================================================================
       SYSTEM NOTIFICATIONS (ANDROID 13+)
       ========================================================================= */

    /**
     * Checks for POST_NOTIFICATIONS permission (Required for Android 13+).
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Routing layout intent targeting the unique notifications preference board.
     */
    public static void openAppNotificationSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /* =========================================================================
       DEVICE ADMINISTRATOR (ANTI-UNINSTALL)
       ========================================================================= */

    /**
     * Determines whether the active policy administration channel is bound.
     */
    public static boolean isDeviceAdminActive(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null) return false;

        ComponentName adminComponent = new ComponentName(context, MonkDeviceAdminReceiver.class);
        return dpm.isAdminActive(adminComponent);
    }

    /**
     * Triggers the full system device administration confirmation panel challenge.
     */
    public static void openDeviceAdminSettings(Context context) {
        ComponentName adminComponent = new ComponentName(context, MonkDeviceAdminReceiver.class);
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Activate to prevent DigitalMonk from being uninstalled without parental permission."
        );
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /* =========================================================================
       VPN CONFIGURATIONS (LOCAL TUNNEL & NATIVE ALWAYS-ON)
       ========================================================================= */

    /**
     * Verifies if local VPN interception plumbing is permitted by the system.
     */
    public static boolean isVpnPermissionGranted(Context context) {
        return android.net.VpnService.prepare(context) == null;
    }

    /**
     * Native Android validation logic verifying if DigitalMonk is registered
     * as the device's persistent, underlying lockdown provider.
     */
    public static boolean isAlwaysOnVpnActive(Context context) {
        String alwaysOnApp = Settings.Secure.getString(
                context.getContentResolver(),
                "always_on_vpn_app"
        );
        return alwaysOnApp != null && alwaysOnApp.equals(context.getPackageName());
    }

    /**
     * Loads the core Android system network configuration VPN list directly.
     */
    public static void openVpnSettings(Context context) {
        Intent intent = new Intent("android.net.vpn.SETTINGS");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /* =========================================================================
       EXACT ALARM OPERATIONS & OEM SPECIFICS
       ========================================================================= */

    /**
     * Confirms runtime availability for high-precision temporal executions (Android 12+).
     */
    public static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    /**
     * OEM Target Routing: Force opens MIUI security panel autostart configurations.
     */
    public static void openXiaomiAutoStartSettings(Context context) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
            // Gracefully ignore on non-Xiaomi setups or unexpected OS variant drops
        }
    }
    public static void openXiaomiBackgroundPopupSettings(Context context) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
            ));
            intent.putExtra("extra_pkgname", context.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
            // Fallback to general security center
            try {
                Intent fallback = new Intent();
                fallback.setComponent(new ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.securitycenter.MainActivity"
                ));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Exception e) {
                // Non-Xiaomi, silently ignore
            }
        }
    }

    public static void openXiaomiPowerSavingSettings(Context context) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
            ));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
            try {
                Intent fallback = new Intent();
                fallback.setComponent(new ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.securitycenter.MainActivity"
                ));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Exception e) {}
        }
    }


}