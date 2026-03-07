package com.example.digitalmonk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Listens for device boot. Accessibility services reconnect automatically on boot,
 * so this receiver's job is minimal — just logging for now.
 * Can be extended to show a notification if the service was disabled.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            Log.d("BootReceiver", "Device booted — Accessibility service will auto-reconnect.")
        }
    }
}