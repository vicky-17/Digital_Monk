package com.example.digitalmonk

import android.app.Application
import com.example.digitalmonk.core.utils.Logger
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.service.WatchdogService
import com.example.digitalmonk.service.notification.NotificationChannels

/**
 * Application entry point.
 *
 * Responsibilities:
 *  1. Create notification channels (must happen before any notification is posted)
 *  2. Start WatchdogService if the app is already set up
 *  3. Schedule the JobScheduler backup
 */
class DigitalMonkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.i("DigitalMonkApp", "App starting…")

        NotificationChannels.createAll(this)

        val prefs = PrefsManager(this)
        if (prefs.hasPin()) {
            // App is set up — start the watchdog immediately.
            // The watchdog will also restart the VPN if it was active.
            WatchdogService.start(this)
            WatchdogService.scheduleJobBackup(this)
        }
    }
}