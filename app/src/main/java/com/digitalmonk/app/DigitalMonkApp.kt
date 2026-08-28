package com.digitalmonk.app

import android.app.Application
import android.util.Log
import com.digitalmonk.app.core.utils.AlarmScheduler.scheduleRepeating
import com.digitalmonk.app.data.local.prefs.PrefsManager
import com.digitalmonk.app.service.WatchdogService
import com.digitalmonk.app.service.notification.NotificationChannels.createAll
import com.google.firebase.auth.FirebaseAuth

/**
 * Why we made this file:
 * This is the "Grand Central Station" of your app. Before any Activity or Service
 * is even created, Android initializes this class.
 * 
 * Responsibilities:
 * 1. Notification Setup: Ensuring categories like "Alerts" exist before the app
 * tries to send one.
 * 2. Auto-Restart: Checking if the parent has already finished the setup (has a PIN).
 * If they have, we immediately wake up the WatchdogService to ensure protection
 * is active from the very first second.
 */
class DigitalMonkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "DigitalMonkApp process starting…")

        // 1. Initialize Firebase Auth (Ensure user is identified)
        ensureUserIdentity()

        // 2. Initialize Notification Channels (Required for Android 8.0+)
        createAll(this)

        // 2. Check if the app setup is complete
        val prefs = PrefsManager(this)

        // If the user has a PIN, it means they've completed the onboarding.
        // We start the "Immortal Guardian" (Watchdog) immediately.
        if (prefs.hasPin()) {
            Log.i(TAG, "Setup complete: Launching Guardian services.")

            // Start the Foreground Watchdog Service
            WatchdogService.start(this)
            scheduleRepeating(this)

            // Schedule the JobScheduler backup (Layer 4 Resilience)
            WatchdogService.scheduleJobBackup(this)
        } else {
            Log.d(TAG, "First-time launch or setup incomplete. Skipping background services.")
        }
    }

    private fun ensureUserIdentity() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.i(TAG, "✅ Anonymous user created: ${it.user?.uid}")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Firebase Anonymous Auth failed: ${e.message}", e)
                }
        } else {
            Log.i(TAG, "✅ Already signed in as: ${auth.currentUser?.uid}")
        }
    }

    companion object {
        private const val TAG = "DigitalMonkApp"
    }
}