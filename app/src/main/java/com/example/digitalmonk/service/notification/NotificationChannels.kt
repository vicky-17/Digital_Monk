package com.example.digitalmonk.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.example.digitalmonk.core.utils.Constants

/**
 * Creates all notification channels at app startup.
 * Must be called from Application.onCreate() before any notification is posted.
 *
 * Add a new channel here whenever a new background service is introduced.
 */
object NotificationChannels {

    fun createAll(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    Constants.CHANNEL_GUARDIAN,
                    "Guardian Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Digital Monk is actively monitoring the device."
                },
                NotificationChannel(
                    Constants.CHANNEL_VPN,
                    "Content Filter",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "DNS-based content filtering is active."
                },
                NotificationChannel(
                    Constants.CHANNEL_SCREEN_TIME,
                    "Screen Time",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Screen time limit warnings and summaries."
                },
                NotificationChannel(
                    Constants.CHANNEL_ALERTS,
                    "Parental Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when a child tries to access blocked content."
                }
            )
        )
    }
}