package com.example.digitalmonk.service.vpn

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.service.vpn.heartbeat.VpnHeartBeatEntity

/**
 * VpnMonitorService — Layer 3 of the VPN keep-alive system.
 *
 * This is a lightweight bound service that runs alongside DnsVpnService.
 * When Android's OEM memory manager kills the VPN process, this bound
 * service is killed too. Its onDestroy() fires — giving us one last chance
 * to restart the VPN.
 *
 * Why this works:
 *   When an OEM kills a foreground service process, Android fires onDestroy()
 *   on all services in that process before terminating it. We use this window
 *   to schedule a restart via startForegroundService().
 *
 *   Note: On very aggressive OEMs (some MIUI builds), even onDestroy() may not
 *   fire. That's why we have the WorkManager watchdog as backup (Layer 1).
 *
 * Pattern from DDG's VpnStateMonitorService.kt:
 *   app-tracking-protection/vpn-impl/.../service/state/VpnStateMonitorService.kt
 *
 * ── How it's connected ──────────────────────────────────────────────────────
 *   DnsVpnService.startVpn() calls bindService() to this monitor.
 *   DnsVpnService.stopVpn() calls unbindService().
 *   This service does NOT start itself — it is only alive while bound.
 */
class VpnMonitorService : Service() {

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): VpnMonitorService = this@VpnMonitorService
    }

    companion object {
        private const val TAG = "VpnMonitorService"

        fun buildIntent(context: Context) = Intent(context, VpnMonitorService::class.java)
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
     * Called when the process is being killed.
     * If the last heartbeat was ALIVE, the VPN was killed unexpectedly —
     * restart it immediately.
     *
     * DDG pattern: vpnBringUpIfSuddenKill()
     */
    override fun onDestroy() {
        Log.w(TAG, "VpnMonitorService onDestroy — checking if VPN needs restart")

        val prefs = PrefsManager(applicationContext)

        // Only attempt restart if:
        //  1. User had VPN enabled
        //  2. Keep-alive is turned on
        //  3. Last heartbeat was ALIVE (i.e. not a clean user-initiated stop)
        if (prefs.safeSearchEnabled &&
            prefs.keepVpnAlive &&
            prefs.lastVpnHeartbeatType == VpnHeartBeatEntity.TYPE_ALIVE
        ) {
            Log.w(TAG, "⚡ Sudden VPN kill detected — restarting DnsVpnService")
            try {
                val intent = Intent(applicationContext, DnsVpnService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(intent)
                } else {
                    applicationContext.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart VPN from monitor service", e)
            }
        } else {
            Log.d(TAG, "No restart needed (safeSearch=${prefs.safeSearchEnabled}, " +
                    "keepAlive=${prefs.keepVpnAlive}, " +
                    "lastHB=${prefs.lastVpnHeartbeatType})")
        }

        super.onDestroy()
    }
}