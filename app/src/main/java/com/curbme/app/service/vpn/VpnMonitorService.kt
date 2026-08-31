package com.curbme.app.service.vpn

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.service.vpn.heartbeat.VpnHeartBeatEntity

/**
 * Why we made this file:
 * This is a "Ghost Service" that runs in the same process as your VPN.
 * Its only job is to die.
 * 
 * When Android's memory manager kills your app's process, it systematically
 * shuts down all services. Because this service is bound to the DnsVpnService,
 * its onDestroy() is guaranteed to fire during a system kill. We use that
 * tiny split-second window to check the last Heartbeat and, if it was "ALIVE",
 * we send a new startForegroundService() intent to the OS.
 * 
 * What the file name defines:
 * "VpnMonitor" indicates its role as a watchdog observer.
 * "Service" identifies it as a standard Android Service component.
 */
class VpnMonitorService : Service() {
    private val binder: IBinder = LocalBinder()

    /**
     * Local Binder class to allow the DnsVpnService to bind to this monitor.
     */
    inner class LocalBinder : Binder() {
        val service: VpnMonitorService
            get() = this@VpnMonitorService
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Bound to VpnMonitorService")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Unbound from VpnMonitorService")
        return super.onUnbind(intent)
    }

    /**
     * Called when the process is being killed by the OS.
     * This is our "Last Stand" logic to revive the VPN.
     */
    override fun onDestroy() {
        Log.w(TAG, "VpnMonitorService onDestroy — checking if VPN needs restart")

        val context = getApplicationContext()
        val prefs = PrefsManager(context)

        // Logic check:
        // 1. Is the feature enabled?
        // 2. Is "Keep Alive" turned on?
        // 3. Did the VPN die while it was supposed to be "ALIVE"?
        if (prefs.isSafeSearchEnabled &&
            prefs.isKeepVpnAlive &&
            VpnHeartBeatEntity.TYPE_ALIVE == prefs.lastVpnHeartbeatType
        ) {
            Log.w(TAG, "⚡ Sudden VPN kill detected — restarting DnsVpnService")
            try {
                val restartIntent = Intent(context, DnsVpnService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(restartIntent)
                } else {
                    context.startService(restartIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart VPN from monitor service", e)
            }
        } else {
            Log.d(TAG, "No restart needed (clean stop or feature disabled)")
        }

        super.onDestroy()
    }

    companion object {
        private const val TAG = "VpnMonitorService"

        /**
         * Static helper to build the intent for binding.
         */
        fun buildIntent(context: Context?): Intent {
            return Intent(context, VpnMonitorService::class.java)
        }
    }
}