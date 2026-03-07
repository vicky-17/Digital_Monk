package com.example.digitalmonk

import android.app.Application
import com.example.digitalmonk.core.utils.Logger
import com.example.digitalmonk.service.notification.NotificationChannels

/**
 * Application class — the single entry point before any Activity/Service.
 *
 * Responsibilities:
 *  - Register notification channels (must happen before any notification is posted)
 *  - Initialise crash reporting (add Crashlytics here when ready)
 *  - Hilt injection entry point (add @HiltAndroidApp when Hilt is added)
 *
 * Add @HiltAndroidApp annotation when you add Hilt dependency.
 */
// @HiltAndroidApp   ← uncomment after adding Hilt
class DigitalMonkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.i("DigitalMonkApp", "App starting…")
        NotificationChannels.createAll(this)
    }
}