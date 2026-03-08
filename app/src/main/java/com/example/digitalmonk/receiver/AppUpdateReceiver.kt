package com.example.digitalmonk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.service.vpn.DnsVpnService
import com.example.digitalmonk.service.vpn.heartbeat.VpnHeartbeatMonitorWorker

/**
 * AppUpdateReceiver — restarts VPN and WorkManager watchdog after app update.
 *
 * When the app is updated (via Play Store or ADB), all running services are
 * killed and the process restarts. This receiver fires ACTION_MY_PACKAGE_REPLACED
 * and re-launches the VPN if it was active before the update.
 *
 * Pattern from Intra: AutoStarter.java handles MY_PACKAGE_REPLACED implicitly.
 * DDG pattern: registers for MY_PACKAGE_REPLACED in manifest.
 */
class AppUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        Log.i(TAG, "App updated — checking if VPN needs restart")

        val prefs = PrefsManager(context)

        if (!prefs.hasPin()) {
            Log.d(TAG, "App not set up — skipping")
            return
        }

        // Re-schedule WorkManager watchdog regardless (it gets cancelled on update)
        if (prefs.safeSearchEnabled && prefs.keepVpnAlive) {
            VpnHeartbeatMonitorWorker.schedule(context)
        }

        // Restart VPN if it was active before the update
        if (prefs.safeSearchEnabled) {
            Log.i(TAG, "Restarting DnsVpnService after app update")
            try {
                val vpnIntent = Intent(context, DnsVpnService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(vpnIntent)
                } else {
                    context.startService(vpnIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart VPN after update", e)
            }
        }
    }

    companion object {
        private const val TAG = "AppUpdateReceiver"
    }
}