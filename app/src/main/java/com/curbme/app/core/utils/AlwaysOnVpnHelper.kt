package com.curbme.app.core.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.curbme.app.receiver.MonkDeviceAdminReceiver

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
object AlwaysOnVpnHelper {
    private const val TAG = "AlwaysOnVpnHelper"

    /**
     * Checks if Always-On VPN is set to our package.
     * Returns null if we can't determine (not device owner).
     */
    fun isAlwaysOnEnabled(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            // Updated to use the actual receiver class found in your project
            val admin = ComponentName(context, MonkDeviceAdminReceiver::class.java)
            val alwaysOnPackage = dpm.getAlwaysOnVpnPackage(admin)
            return context.getPackageName() == alwaysOnPackage
        } catch (e: Exception) {
            Log.d(TAG, "Not device owner — can't check always-on: " + e.message)
            return null
        }
    }

    /**
     * Programmatically enables Always-On VPN.
     * Requires Device Owner privileges.
     */
    fun enableAlwaysOnVpn(context: Context, lockdown: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false

        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, MonkDeviceAdminReceiver::class.java)
            dpm.setAlwaysOnVpnPackage(admin, context.getPackageName(), lockdown)
            Log.i(TAG, "✅ Always-on VPN enabled (lockdown=" + lockdown + ")")
            return true
        } catch (e: SecurityException) {
            Log.w(TAG, "Not device owner — cannot set always-on programmatically")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable always-on VPN", e)
            return false
        }
    }

    /**
     * Opens the system VPN settings screen for manual configuration.
     */
    fun openVpnSettings(context: Context) {
        try {
            val intent: Intent?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent = Intent(Settings.ACTION_VPN_SETTINGS)
            } else {
                intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open VPN settings", e)
        }
    }

    val setupInstructions: String
        /**
         * Returns instructions text to show the user for enabling Always-On VPN.
         */
        get() = "To keep CurbMe's filter always active:\n\n" +
                "1. Open the notification just shown\n" +
                "2. Tap \"Settings\" → \"VPN\"\n" +
                "3. Find \"CurbMe Shield\"\n" +
                "4. Tap the ⚙️ gear icon\n" +
                "5. Enable \"Always-on VPN\"\n" +
                "6. Optional: Enable \"Block connections without VPN\" for strict mode\n\n" +
                "This prevents other apps from disabling the filter."
}