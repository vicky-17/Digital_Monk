package com.digitalmonk.app.core.deviceowner

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

object DevicePolicyHelper {
    private const val TAG = "DevicePolicyHelper"

    /**
     * Applies (or clears) the Private DNS configuration using the sanctioned
     * DevicePolicyManager APIs for device owners. Settings.Global.putString()
     * requires WRITE_SECURE_SETTINGS, which device owner status alone does NOT
     * grant — that was the root cause of the previous silent failure.
     *
     * NOTE: setGlobalPrivateDnsModeSpecifiedHost() performs a blocking network
     * connectivity check (RFC 7858 DoT handshake) to validate the host, so this
     * MUST be called off the main thread. Callers are responsible for that.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun applyPrivateDns(context: Context, enabled: Boolean, hostname: String): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, com.digitalmonk.app.receiver.MonkDeviceAdminReceiver::class.java)

        if (dpm == null || !dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "Cannot apply Private DNS: App is not Device Owner")
            return false
        }

        return try {
            if (enabled && hostname.isNotBlank()) {
                when (val result = dpm.setGlobalPrivateDnsModeSpecifiedHost(adminComponent, hostname)) {
                    DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR -> {
                        Log.i(TAG, "Private DNS set to hostname mode: $hostname")
                        true
                    }
                    DevicePolicyManager.PRIVATE_DNS_SET_ERROR_HOST_NOT_SERVING -> {
                        Log.w(TAG, "Host $hostname does not support DNS-over-TLS")
                        false
                    }
                    DevicePolicyManager.PRIVATE_DNS_SET_ERROR_FAILURE_SETTING -> {
                        Log.w(TAG, "General failure setting Private DNS (code=$result)")
                        false
                    }
                    else -> false
                }
            } else {
                dpm.setGlobalPrivateDnsModeOpportunistic(adminComponent)
                Log.i(TAG, "Private DNS reset to opportunistic mode")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply Private DNS configuration: ${e.message}")
            false
        }
    }
}