package com.example.digitalmonk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.digitalmonk.core.utils.Logger

/**
 * Fires on device boot. The AccessibilityService auto-reconnects; this
 * receiver can be extended to restart any other services that don't
 * auto-reconnect (e.g., VPN service, overlay service).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        Logger.i(TAG, "Device booted — services will auto-reconnect.")

        // TODO Phase 2: If VPN filter was active before reboot, restart DnsVpnService here.
        // TODO Phase 3: Post a notification if accessibility service was disabled before reboot.
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}