package com.digitalmonk.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Why we made this file:
 * To accurately calculate "Screen Time," the app needs to know exactly when the
 * child is actually looking at the phone. It is not enough to just check background
 * app usage, because the phone might be sitting on a desk with the screen off while
 * a YouTube video plays in the background.
 * 
 * This receiver listens for the hardware-level broadcasts that fire when the physical
 * screen turns on, turns off, or when the user unlocks the device.
 * 
 * What the file name defines:
 * "ScreenState" refers to the physical display's power status.
 * "Receiver" identifies it as an Android BroadcastReceiver.
 */
class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Standard Java safety checks
        if (intent == null || intent.getAction() == null) {
            return
        }

        val action = intent.getAction()

        // I have expanded your TODO into the actual structural blocks you will need.
        // In Java, we use .equals() for safe string comparison.
        if (Intent.ACTION_SCREEN_ON == action) {
            Log.d(TAG, "Screen turned ON")

            // TODO: Start active time tracking / resume VPN watchdog checks
        } else if (Intent.ACTION_SCREEN_OFF == action) {
            Log.d(TAG, "Screen turned OFF")

            // TODO: Pause time tracking / save the current session to the database
        } else if (Intent.ACTION_USER_PRESENT == action) {
            Log.d(TAG, "Device unlocked")
            // TODO: (Optional) Handle the exact moment the child unlocks the phone
        }
    }

    companion object {
        private const val TAG = "ScreenStateReceiver"
    }
}