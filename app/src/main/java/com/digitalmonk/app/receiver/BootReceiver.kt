package com.digitalmonk.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.digitalmonk.app.core.deviceowner.DevicePolicyHelper
import com.digitalmonk.app.core.utils.AlarmScheduler
import com.digitalmonk.app.data.local.prefs.PrefsManager
import com.digitalmonk.app.service.WatchdogService
import com.digitalmonk.app.service.vpn.DnsVpnService

/**
 * Why we made this file:
 * When an Android device reboots, standard background and foreground services
 * do NOT automatically restart. For a parental control app, a child rebooting
 * the phone is a common tactic used to try and bypass restrictions.
 * 
 * This BroadcastReceiver listens for the system's "BOOT_COMPLETED" broadcast
 * and immediately turns the Watchdog and VPN services back on, ensuring
 * the protection remains active without the parent needing to open the app.
 * 
 * What the file name defines:
 * "Boot" refers to the device startup process.
 * "Receiver" identifies the Android component (BroadcastReceiver).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Safe check for null intent and action in Java
        if (intent == null) return

        val action = intent.getAction()
        if (action == null) return

        // Using .equals() for safe string comparison to prevent NullPointerExceptions
        if ((Intent.ACTION_BOOT_COMPLETED != action) && (Intent.ACTION_LOCKED_BOOT_COMPLETED != action) && ("android.intent.action.QUICKBOOT_POWERON" != action) && ("com.htc.intent.action.QUICKBOOT_POWERON" != action) // HTC variant
        ) {
            return
        }

        Log.i(TAG, "Boot completed — starting Digital Monk services")

        val prefs = PrefsManager(context)
        if (prefs.isPrivateDnsEnabled) {
            DevicePolicyHelper.applyPrivateDns(context, true, prefs.selectedPrivateDnsHostname)
        }
        // Only start services if the app was set up (has a PIN)
        if (!prefs.hasPin()) {
            Log.i(TAG, "App not set up yet — skipping service start")
            return
        }

        // 1. Always start WatchdogService — it's the root guardian
        WatchdogService.start(context)

        AlarmScheduler.scheduleRepeating(context)

        // 2. Restart VPN if it was active before reboot
        if (prefs.isSafeSearchEnabled) {
            Log.i(TAG, "Restarting DnsVpnService (was active before reboot)")
            try {
                val vpnIntent = Intent(context, DnsVpnService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(vpnIntent)
                } else {
                    context.startService(vpnIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart DnsVpnService on boot", e)
                // WatchdogService will retry in its health check loop
            }
        }

        // 3. Re-schedule the JobScheduler backup
        WatchdogService.scheduleJobBackup(context)

        Log.i(TAG, "✅ All services started after boot")
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}