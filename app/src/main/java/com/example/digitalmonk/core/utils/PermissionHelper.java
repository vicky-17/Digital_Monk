package com.example.digitalmonk.core.utils;

import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.example.digitalmonk.service.accessibility.GuardianAccessibilityService;

/**
 * Why we made this file:
 * Digital Monk is a high-privilege application that requires sensitive Android permissions
 * to function (like reading app usage or drawing over other apps).
 * * We created this helper to centralize all "Permission Checks." Instead of writing complex
 * logic in every Activity to see if a permission is granted, we call these simple methods.
 * This makes the code cleaner and ensures that if Android updates its permission model,
 * we only have to fix it in this one file.
 *
 * What the file name defines:
 * "Permission" refers to the Android security framework.
 * "Helper" identifies this as a utility class providing static logic to simplify
 * permission state verification.
 */
public class PermissionHelper {

    // Suppress constructor as this is a utility class
    private PermissionHelper() {}

    /**
     * Accessibility service — required for Shorts blocking & App blocking.
     * We check if 'GuardianAccessibilityService' is currently active in system settings.
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
     * SYSTEM_ALERT_WINDOW — required for overlay / block screen.
     */
    public static boolean canDrawOverlays(Context context) {
        return Settings.canDrawOverlays(context);
    }

    /**
     * PACKAGE_USAGE_STATS — required for screen time tracking.
     * Uses AppOpsManager to check if the user has allowed the app to see usage data.
     */
    public static boolean hasUsageStatsPermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.getPackageName()
            );
        } else {
            mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.getPackageName()
            );
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * Checks for POST_NOTIFICATIONS permission (Required for Android 13+).
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Permissions are granted at install time on older Android versions
            return true;
        }
    }
}