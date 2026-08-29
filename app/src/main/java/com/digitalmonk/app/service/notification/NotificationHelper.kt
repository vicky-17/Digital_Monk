package com.digitalmonk.app.service.notification

import android.R
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.digitalmonk.app.core.utils.Constants

/**
 * Why we made this file:
 * Constructing notifications in Android requires a lot of boilerplate code
 * (Builders, PendingIntents, Icons). Instead of writing this logic directly
 * inside your background services or UI, we centralize it here.
 * 
 * This keeps your core services clean and ensures all notifications have a
 * consistent look and feel across the entire app.
 * 
 * What the file name defines:
 * "Notification" indicates the Android component being handled.
 * "Helper" dictates its architectural role as a stateless utility class.
 */
object NotificationHelper {
    // Unique IDs for your notifications so they can be updated or dismissed later
    const val FOREGROUND_SERVICE_ID: Int = 1001
    private const val ALERT_NOTIFICATION_ID = 1002
    private const val WARNING_NOTIFICATION_ID = 1003

    /**
     * Builds the persistent, silent notification required to keep your
     * Watchdog or VPN services running infinitely in the background without
     * Android killing them.
     */
    @JvmStatic
    fun buildGuardianForegroundNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, Constants.CHANNEL_GUARDIAN)
            .setSmallIcon(com.digitalmonk.app.R.mipmap.ic_launcher)
            .setContentTitle("Digital Monk Protection Active")
            .setContentText("Keeping this device safe in the background.")
            .setPriority(NotificationCompat.PRIORITY_LOW) // Keeps it silent
            .setOngoing(true) // Prevents the child from swiping it away
            .build()
    }

    /**
     * Builds a notification that shows real-time app usage stats and regain timers.
     * Uses Android's "Live Update" pattern for real-time status chips and promoted ongoing status.
     */
    fun buildUsageNotification(
        context: Context,
        appName: String?,
        appUsageMs: Long,
        totalUsageMs: Long,
        launchCount: Int,
        bypassRemainingMs: Long = 0L,
        sessionDurationMs: Long = 0L
    ): Notification {
        val title: String
        val text: String

        if (bypassRemainingMs > 0) {
            title = "⏳ Regain active: ${com.digitalmonk.app.core.utils.TimeUtils.formatDuration(bypassRemainingMs)} left"
            text = "Total Usage: ${com.digitalmonk.app.core.utils.TimeUtils.formatDuration(totalUsageMs)}"
        } else if (appName != null) {
            // App Open Design
            title = "$appName: ${com.digitalmonk.app.core.utils.TimeUtils.formatDuration(appUsageMs)}"
            text = "Total Usage: ${com.digitalmonk.app.core.utils.TimeUtils.formatDuration(totalUsageMs)}"
        } else {
            // Home Screen / System Design
            title = "Total Usage: ${com.digitalmonk.app.core.utils.TimeUtils.formatDuration(totalUsageMs)}"
            text = "Digital Monk protection is active."
        }

        val openIntent = android.app.PendingIntent.getActivity(
            context, 0, android.content.Intent(context, com.digitalmonk.app.ui.MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, Constants.CHANNEL_GUARDIAN)
            .setSmallIcon(com.digitalmonk.app.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())

        return builder.build()
    }

    /**
     * Fires a high-priority alert (heads-up notification) when a child
     * tries to open a blocked app or access a forbidden website.
     */
    fun showBlockAlert(context: Context, blockedItemName: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (manager == null) return

        val builder = NotificationCompat.Builder(context, Constants.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_dialog_alert)
            .setContentTitle("Access Blocked \uD83D\uDEE1\uFE0F")
            .setContentText("Attempted to access restricted content: " + blockedItemName)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Pops down from the top of the screen
            .setAutoCancel(true)

        manager.notify(ALERT_NOTIFICATION_ID, builder.build())
    }

    /**
     * Fires a warning when a child is approaching their daily screen time limit.
     */
    fun showTimeWarning(context: Context, appName: String, minutesLeft: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (manager == null) return

        val builder = NotificationCompat.Builder(context, Constants.CHANNEL_SCREEN_TIME)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle("Screen Time Warning")
            .setContentText("Only " + minutesLeft + " minutes remaining for " + appName + ".")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        // We use the app's hashcode as the ID so warnings for different apps don't
        // overwrite each other in the notification tray!
        manager.notify(appName.hashCode(), builder.build())
    }
}