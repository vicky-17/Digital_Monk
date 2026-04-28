package com.example.digitalmonk;

import android.app.Application;
import android.util.Log;

import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.WatchdogService;
import com.example.digitalmonk.service.notification.NotificationChannels;
import com.example.digitalmonk.core.utils.AlarmScheduler;

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
public class DigitalMonkApp extends Application {

    private static final String TAG = "DigitalMonkApp";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "DigitalMonkApp process starting…");

        // 1. Initialize Notification Channels (Required for Android 8.0+)
        NotificationChannels.createAll(this);

        // 2. Check if the app setup is complete
        PrefsManager prefs = new PrefsManager(this);

        // If the user has a PIN, it means they've completed the onboarding.
        // We start the "Immortal Guardian" (Watchdog) immediately.
        if (prefs.hasPin()) {
            Log.i(TAG, "Setup complete: Launching Guardian services.");

            // Start the Foreground Watchdog Service
            WatchdogService.start(this);
            AlarmScheduler.scheduleRepeating(this);

            // Schedule the JobScheduler backup (Layer 4 Resilience)
            WatchdogService.scheduleJobBackup(this);
        } else {
            Log.d(TAG, "First-time launch or setup incomplete. Skipping background services.");
        }
    }
}