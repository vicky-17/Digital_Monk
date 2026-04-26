package com.example.digitalmonk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.service.WatchdogService
import com.example.digitalmonk.service.vpn.DnsVpnService

/**
 * Fires on device boot — restarts all Digital Monk services.
 *
 * Why this is needed:
 *  - Foreground services do NOT auto-restart on reboot (unlike AccessibilityService which does)
 *  - The WatchdogService and DnsVpnService must be explicitly restarted after every boot
 *
 * Requires RECEIVE_BOOT_COMPLETED permission in AndroidManifest.xml.
 *
 * ⚠️ On MIUI / EMUI / ColorOS:
 *  The user MUST add this app to the "Autostart" whitelist in system settings,
 *  otherwise this receiver will never fire after reboot.
 *  We prompt the user to do this in PermissionSetupScreen.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&  // HTC/some Chinese OEMs
            action != "com.htc.intent.action.QUICKBOOT_POWERON"     // HTC variant
        ) return

        Log.i(TAG, "Boot completed — starting Digital Monk services")

        val prefs = PrefsManager(context)

        // Only start services if the app was set up (has a PIN)
        if (!prefs.hasPin()) {
            Log.i(TAG, "App not set up yet — skipping service start")
            return
        }

        // 1. Always start WatchdogService — it's the root guardian
        WatchdogService.start(context)

        // 2. Restart VPN if it was active before reboot
        if (prefs.safeSearchEnabled) {
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