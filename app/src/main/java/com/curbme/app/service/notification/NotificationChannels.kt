package com.curbme.app.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.curbme.app.core.utils.Constants

/**
 * Why we made this file:
 * Since Android 8.0 (Oreo), all notifications must be assigned to a specific "Channel".
 * For a parental control application, categorizing these notifications is essential
 * so the system (and the parent) knows which alerts are silent background trackers
 * (like the VPN) and which are urgent (like bypass attempts).
 * 
 * This utility class creates those channels when the application first starts.
 */
object NotificationChannels {
    /**
     * Creates all notification channels at app startup.
     * Must be called from Application.onCreate() before any notification is posted.
     */
    @JvmStatic
    fun createAll(context: Context) {
        // Notification channels are only required on Android O (API 26) and above.
        // It is a standard Java/Android practice to guard channel creation with this check.
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (manager == null) return

        val channels: MutableList<NotificationChannel?> = ArrayList<NotificationChannel?>()

        // 1. Guardian Service Channel (Silent/Low Importance)
        val guardianChannel = NotificationChannel(
            Constants.CHANNEL_GUARDIAN,
            "Guardian Service",
            NotificationManager.IMPORTANCE_LOW
        )
        guardianChannel.setDescription("CurbMe is actively monitoring the device.")
        channels.add(guardianChannel)

        // 2. VPN / Content Filter Channel (Silent/Low Importance)
        val vpnChannel = NotificationChannel(
            Constants.CHANNEL_VPN,
            "Content Filter",
            NotificationManager.IMPORTANCE_LOW
        )
        vpnChannel.setDescription("DNS-based content filtering is active.")
        channels.add(vpnChannel)

        // 3. Screen Time Channel (Default Importance - makes a sound)
        val screenTimeChannel = NotificationChannel(
            Constants.CHANNEL_SCREEN_TIME,
            "Screen Time",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        screenTimeChannel.setDescription("Screen time limit warnings and summaries.")
        channels.add(screenTimeChannel)

        // 4. Alerts Channel (High Importance - pops up on screen)
        val alertsChannel = NotificationChannel(
            Constants.CHANNEL_ALERTS,
            "Parental Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        alertsChannel.setDescription("Alerts when a child tries to access blocked content.")
        channels.add(alertsChannel)

        // Silent channel for overlay service — no sound, no popup, hidden from shade
        val silentChannel = NotificationChannel(
            Constants.CHANNEL_SILENT,
            "Background Protection",
            NotificationManager.IMPORTANCE_MIN // ← lowest possible, hidden from shade
        )
        silentChannel.setSound(null, null)
        silentChannel.enableVibration(false)
        silentChannel.enableLights(false)
        silentChannel.setShowBadge(false)
        channels.add(silentChannel)

        // Register all channels at once
        manager.createNotificationChannels(channels)
    }
}