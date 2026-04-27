package com.example.digitalmonk.core.utils;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.digitalmonk.receiver.MonkDeviceAdminReceiver;

/**
 * Why we made this file:
 * This utility class manages the "Always-On VPN" feature, which is critical for
 * a parental control app. It ensures that the VPN (which filters content)
 * cannot be easily turned off by the child. It provides methods to check the
 * status, enable it programmatically (if the app has Device Owner privileges),
 * or guide the user to the system settings to enable it manually.
 *
 * What the file name defines:
 * "AlwaysOnVpn" refers to the specific Android system feature being managed.
 * "Helper" identifies this as a utility class containing static methods to
 * simplify complex system interactions.
 */
public class AlwaysOnVpnHelper {

    private static final String TAG = "AlwaysOnVpnHelper";

    /**
     * Checks if Always-On VPN is set to our package.
     * Returns null if we can't determine (not device owner).
     */
    @Nullable
    public static Boolean isAlwaysOnEnabled(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null;

        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            // Updated to use the actual receiver class found in your project
            ComponentName admin = new ComponentName(context, MonkDeviceAdminReceiver.class);
            String alwaysOnPackage = dpm.getAlwaysOnVpnPackage(admin);
            return context.getPackageName().equals(alwaysOnPackage);
        } catch (Exception e) {
            Log.d(TAG, "Not device owner — can't check always-on: " + e.getMessage());
            return null;
        }
    }

    /**
     * Programmatically enables Always-On VPN.
     * Requires Device Owner privileges.
     */
    public static boolean enableAlwaysOnVpn(Context context, boolean lockdown) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;

        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(context, MonkDeviceAdminReceiver.class);
            dpm.setAlwaysOnVpnPackage(admin, context.getPackageName(), lockdown);
            Log.i(TAG, "✅ Always-on VPN enabled (lockdown=" + lockdown + ")");
            return true;
        } catch (SecurityException e) {
            Log.w(TAG, "Not device owner — cannot set always-on programmatically");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable always-on VPN", e);
            return false;
        }
    }

    /**
     * Opens the system VPN settings screen for manual configuration.
     */
    public static void openVpnSettings(Context context) {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent = new Intent(Settings.ACTION_VPN_SETTINGS);
            } else {
                intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Could not open VPN settings", e);
        }
    }

    /**
     * Returns instructions text to show the user for enabling Always-On VPN.
     */
    public static String getSetupInstructions() {
        return "To keep Digital Monk's filter always active:\n\n" +
                "1. Open the notification just shown\n" +
                "2. Tap \"Settings\" → \"VPN\"\n" +
                "3. Find \"Digital Monk Shield\"\n" +
                "4. Tap the ⚙️ gear icon\n" +
                "5. Enable \"Always-on VPN\"\n" +
                "6. Optional: Enable \"Block connections without VPN\" for strict mode\n\n" +
                "This prevents other apps from disabling the filter.";
    }
}