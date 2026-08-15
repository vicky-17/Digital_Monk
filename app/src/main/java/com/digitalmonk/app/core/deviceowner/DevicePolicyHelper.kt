package com.digitalmonk.app.core.deviceowner

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log

object DevicePolicyHelper {
    private const val TAG = "DevicePolicyHelper"

    fun applyPrivateDns(context: Context, enabled: Boolean, hostname: String) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, com.digitalmonk.app.receiver.MonkDeviceAdminReceiver::class.java)

        if (dpm == null || !dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "Cannot apply Private DNS: App is not Device Owner")
            return
        }

        try {
            val resolver = context.contentResolver
            if (enabled && hostname.isNotBlank()) {
                Settings.Global.putString(resolver, "private_dns_mode", "hostname")
                Settings.Global.putString(resolver, "private_dns_specifier", hostname)
                Log.i(TAG, "Private DNS set to hostname mode: $hostname")
            } else {
                Settings.Global.putString(resolver, "private_dns_mode", "opportunistic")
                Settings.Global.putString(resolver, "private_dns_specifier", "")
                Log.i(TAG, "Private DNS reset to opportunistic mode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply Private DNS configuration: ${e.message}")
        }
    }
}
