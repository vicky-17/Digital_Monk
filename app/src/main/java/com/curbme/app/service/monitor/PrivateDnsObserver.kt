package com.curbme.app.service.monitor

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.curbme.app.core.deviceowner.DevicePolicyHelper

/**
 * Official ContentObserver for Android System Private DNS Settings.
 * Listens directly to "private_dns_mode" and "private_dns_specifier" system URIs.
 * When a system DNS change occurs, it invokes DevicePolicyHelper.reapplyPolicyIfMismatched.
 */
class PrivateDnsObserver(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "PrivateDnsObserver"
    }

    private var isRegistered = false

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        Log.i(TAG, "System Private DNS change detected!")
        DevicePolicyHelper.reapplyPolicyIfMismatched(context)
    }

    /**
     * Registers listener on private_dns_mode and private_dns_specifier URIs.
     */
    fun register() {
        if (isRegistered) return
        try {
            val cr = context.contentResolver
            val modeUri = Settings.Global.getUriFor("private_dns_mode")
            val hostUri = Settings.Global.getUriFor("private_dns_specifier")

            cr.registerContentObserver(modeUri, true, this)
            cr.registerContentObserver(hostUri, true, this)
            isRegistered = true
            Log.i(TAG, "PrivateDnsObserver registered successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register PrivateDnsObserver: ${e.message}", e)
        }
    }

    /**
     * Unregisters listener when service stops.
     */
    fun unregister() {
        if (!isRegistered) return
        try {
            context.contentResolver.unregisterContentObserver(this)
            isRegistered = false
            Log.i(TAG, "PrivateDnsObserver unregistered.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister PrivateDnsObserver: ${e.message}", e)
        }
    }
}