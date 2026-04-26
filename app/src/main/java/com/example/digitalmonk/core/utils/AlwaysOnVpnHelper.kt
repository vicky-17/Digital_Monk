package com.example.digitalmonk.core.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Handles "Always-On VPN" setup for Digital Monk.
 *
 * ── What is Always-On VPN? ──────────────────────────────────────────────────
 * Android can be told to keep a VPN connected permanently and prevent any
 * traffic from flowing if the VPN drops ("lockdown mode").
 * This means even if the user or another app tries to override our VPN,
 * Android blocks all traffic until ours reconnects.
 *
 * ── How to enable it ────────────────────────────────────────────────────────
 * There are THREE methods, from easiest to most powerful:
 *
 * METHOD 1: Guide the user (Consumer apps — what we use)
 *   Deep-link to Settings → Network → VPN → Digital Monk → ⚙️ → "Always-on VPN"
 *   The user taps one toggle. We can detect if it's set.
 *
 * METHOD 2: Device Owner API (Enterprise/MDM — most powerful)
 *   If the app is set as Device Owner (via ADB or MDM enrollment),
 *   we can call DevicePolicyManager.setAlwaysOnVpnPackage() programmatically.
 *   This is what parental control apps like Bark, Circle use on managed devices.
 *   ⚠️  Requires: adb shell dpm set-device-owner com.example.digitalmonk/.DeviceAdminReceiver
 *
 * METHOD 3: Android for Work / Profile Owner
 *   For work profiles — not relevant for consumer parental control.
 *
 * ── Blocking other VPNs ─────────────────────────────────────────────────────
 * Android only allows ONE VPN to be active at a time.
 * When our VPN is running, no other VPN can connect without:
 *   1. The user explicitly disconnecting ours first
 *   2. Or our VPN being stopped
 * With "Always-on VPN" enabled, the user CAN still switch — but lockdown mode
 * blocks all traffic during the switch, making it inconvenient.
 * With Device Owner, we can call setAlwaysOnVpnPackage() with lockdown=true
 * and the user CANNOT disable it from Settings.
 */
object AlwaysOnVpnHelper {

    private const val TAG = "AlwaysOnVpnHelper"

    /**
     * Checks if Always-On VPN is set to our package.
     * Only works on Android 8.0+ with Device Owner.
     * Returns null if we can't determine (not device owner).
     */
    fun isAlwaysOnEnabled(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, DeviceAdminReceiver::class.java)
            val alwaysOnPackage = dpm.getAlwaysOnVpnPackage(admin)
            alwaysOnPackage == context.packageName
        } catch (e: Exception) {
            Log.d(TAG, "Not device owner — can't check always-on: ${e.message}")
            null  // Not device owner, can't check
        }
    }

    /**
     * Programmatically enables Always-On VPN.
     * ⚠️ Only works if this app is the Device Owner.
     *
     * To make this app Device Owner (for testing):
     *   adb shell dpm set-device-owner com.example.digitalmonk/.receiver.DeviceAdminReceiver
     *
     * @param lockdown If true, all traffic is blocked when VPN is disconnected.
     *                 This prevents bypass but can cause issues if service crashes.
     */
    fun enableAlwaysOnVpn(context: Context, lockdown: Boolean = false): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false

        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, DeviceAdminReceiver::class.java)
            dpm.setAlwaysOnVpnPackage(admin, context.packageName, lockdown)
            Log.i(TAG, "✅ Always-on VPN enabled (lockdown=$lockdown)")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Not device owner — cannot set always-on programmatically")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable always-on VPN", e)
            false
        }
    }

    /**
     * Opens the system VPN settings screen.
     * The user can then tap Digital Monk → gear icon → "Always-on VPN" toggle.
     *
     * This is the METHOD 1 flow for consumer apps.
     */
    fun openVpnSettings(context: Context) {
        try {
            // Android 8+ has a direct VPN settings page
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Intent(Settings.ACTION_VPN_SETTINGS)
            } else {
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open VPN settings", e)
        }
    }

    /**
     * Returns instructions text to show the user for enabling Always-On VPN.
     */
    fun getSetupInstructions(): String = """
        To keep Digital Monk's filter always active:
        
        1. Open the notification just shown
        2. Tap "Settings" → "VPN"  
        3. Find "Digital Monk Shield"
        4. Tap the ⚙️ gear icon
        5. Enable "Always-on VPN"
        6. Optional: Enable "Block connections without VPN" for strict mode
        
        This prevents other apps from disabling the filter.
    """.trimIndent()
}

/**
 * Device Admin Receiver — required to receive device admin callbacks
 * and to be set as Device Owner.
 *
 * Add this to AndroidManifest.xml:
 * <receiver android:name=".receiver.DeviceAdminReceiver"
 *           android:exported="true"
 *           android:permission="android.permission.BIND_DEVICE_ADMIN">
 *     <meta-data android:name="android.app.device_admin"
 *                android:resource="@xml/device_admin" />
 *     <intent-filter>
 *         <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
 *     </intent-filter>
 * </receiver>
 */
class DeviceAdminReceiver : android.app.admin.DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Log.i("DeviceAdmin", "Device admin enabled")
    }
    override fun onDisabled(context: Context, intent: Intent) {
        Log.w("DeviceAdmin", "Device admin disabled")
    }
}