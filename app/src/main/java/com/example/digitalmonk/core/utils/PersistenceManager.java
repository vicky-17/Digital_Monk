package com.example.digitalmonk.core.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

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
public class PersistenceManager {

    private static final String TAG = "PersistenceManager";

    public enum OemType {
        XIAOMI, OPPO, VIVO, HUAWEI, SAMSUNG, ONEPLUS, ASUS, GENERIC
    }

    private PersistenceManager() {}

    // ── Battery Optimization ──────────────────────────────────────────────────

    public static boolean isBatteryOptimizationDisabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            // FIXED: context.packageName() -> context.getPackageName()
            return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
        }
        return true;
    }

    public static Intent buildBatteryOptimizationIntent(Context context) {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        // FIXED: context.packageName() -> context.getPackageName()
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        return intent;
    }

    // ── Display Over Other Apps ───────────────────────────────────────────────

    public static boolean canDrawOverlays(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public static Intent buildOverlayPermissionIntent(Context context) {
        return new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                // FIXED: context.packageName() -> context.getPackageName()
                Uri.parse("package:" + context.getPackageName())
        );
    }

    // ── Usage Stats ───────────────────────────────────────────────────────────

    public static boolean hasUsageStatsPermission(Context context) {
        return PermissionHelper.hasUsageStatsPermission(context);
    }

    public static Intent buildUsageStatsIntent() {
        return new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
    }

    // ── OEM Autostart Logic ───────────────────────────────────────────────────

    public static OemType detectOem() {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")) return OemType.XIAOMI;
        if (manufacturer.contains("oppo") || manufacturer.contains("realme")) return OemType.OPPO;
        if (manufacturer.contains("vivo")) return OemType.VIVO;
        if (manufacturer.contains("huawei") || manufacturer.contains("honor")) return OemType.HUAWEI;
        if (manufacturer.contains("samsung")) return OemType.SAMSUNG;
        if (manufacturer.contains("oneplus")) return OemType.ONEPLUS;
        if (manufacturer.contains("asus")) return OemType.ASUS;
        return OemType.GENERIC;
    }

    public static Intent buildAutostartIntent(Context context) {
        List<Intent> intents = new ArrayList<>();
        OemType oem = detectOem();

        switch (oem) {
            case XIAOMI:
                intents.add(new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")));
                intents.add(new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.securitycenter.MainActivity")));
                break;
            case OPPO:
                intents.add(new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.FakeActivity")));
                intents.add(new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.FakeActivity")));
                intents.add(new Intent().setComponent(new ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.powersave.PowerUsageModelActivity")));
                break;
            case VIVO:
                intents.add(new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")));
                intents.add(new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")));
                break;
            case HUAWEI:
                intents.add(new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")));
                intents.add(new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")));
                break;
            case SAMSUNG:
                intents.add(new Intent().setComponent(new ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")));
                break;
            case ONEPLUS:
                intents.add(new Intent().setComponent(new ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")));
                break;
            case ASUS:
                intents.add(new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutostartActivity")));
                break;
            default:
                break;
        }

        for (Intent intent : intents) {
            if (isIntentResolvable(context, intent)) {
                return intent;
            }
        }
        return null;
    }

    public static String getAutostartInstructions() {
        switch (detectOem()) {
            case XIAOMI: return "Security → Manage Apps → Digital Monk → Autostart → Enable";
            case OPPO:   return "Phone Manager → Privacy Permissions → Startup Manager → Digital Monk → Allow";
            case VIVO:   return "iManager → App Manager → Autostart → Digital Monk → Enable";
            case HUAWEI: return "System Manager → App Launch → Digital Monk → Manage manually → Enable all";
            case SAMSUNG: return "Device Care → Battery → Background Usage Limits → Never Sleeping Apps → Add Digital Monk";
            case ONEPLUS: return "Settings → Battery → Battery Optimization → Digital Monk → Don't Optimize";
            case ASUS:   return "Mobile Manager → Autostart → Digital Monk → Enable";
            default:     return "Settings → Battery → Battery Optimization → Digital Monk → Don't Optimize";
        }
    }

    public static Intent buildMiuiPowerKeeperIntent(Context context) {
        if (detectOem() != OemType.XIAOMI) return null;
        Intent intent = new Intent().setComponent(new ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"));
        return isIntentResolvable(context, intent) ? intent : null;
    }

    public static boolean hasOemAutostartSetting(Context context) {
        return buildAutostartIntent(context) != null;
    }

    private static boolean isIntentResolvable(Context context, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return context.getPackageManager().resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0)) != null;
            } else {
                return context.getPackageManager().resolveActivity(intent, 0) != null;
            }
        } catch (Exception e) {
            return false;
        }
    }
}