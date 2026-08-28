package com.digitalmonk.app

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Process
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
        val processName = getCurrentProcessName(this)
        Log.i(TAG, "DigitalMonkApp process starting: $processName")

        if (isMainProcess(this)) {
            Log.i(TAG, "Main process initialized. Setting up UI-related components.")
            // 1. Initialize Firebase Auth (Ensure user is identified)
            ensureUserIdentity()
            
            // 2. Initialize Notification Channels (Required for Android 8.0+)
            createAll(this)

            // 3. Start background services from Main process (will spawn :guardian)
            val prefs = PrefsManager(this)
            if (prefs.hasPin()) {
                Log.i(TAG, "Setup complete: Launching Guardian services.")
                WatchdogService.start(this)
                scheduleRepeating(this)
                WatchdogService.scheduleJobBackup(this)
            }
        } else if (processName?.endsWith(":guardian") == true) {
            Log.i(TAG, "Guardian process initialized. Setting up background components.")
            createAll(this) // Notifications needed here too
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

        fun isMainProcess(context: Context): Boolean {
            val processName = getCurrentProcessName(context)
            return processName == null || processName == context.packageName
        }

        private fun getCurrentProcessName(context: Context): String? {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                return Application.getProcessName()
            }
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val processes = am.runningAppProcesses
            if (processes != null) {
                for (process in processes) {
                    if (process.pid == Process.myPid()) {
                        return process.processName
                    }
                }
            }
            return null
        }
    }
}
