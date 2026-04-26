package com.example.digitalmonk.service.notification;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.example.digitalmonk.core.utils.Constants;

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
public class NotificationHelper {

    // Unique IDs for your notifications so they can be updated or dismissed later
    public static final int FOREGROUND_SERVICE_ID = 1001;
    private static final int ALERT_NOTIFICATION_ID = 1002;
    private static final int WARNING_NOTIFICATION_ID = 1003;

    /**
     * Private constructor to enforce the Utility Class pattern.
     */
    private NotificationHelper() {}

    /**
     * Builds the persistent, silent notification required to keep your
     * Watchdog or VPN services running infinitely in the background without
     * Android killing them.
     */
    public static Notification buildGuardianForegroundNotification(Context context) {
        // TODO: Replace android.R.drawable.ic_secure with your own R.drawable.ic_monk_logo

        return new NotificationCompat.Builder(context, Constants.CHANNEL_GUARDIAN)
                .setSmallIcon(android.R.drawable.ic_secure)
                .setContentTitle("Digital Monk Protection Active")
                .setContentText("Keeping this device safe in the background.")
                .setPriority(NotificationCompat.PRIORITY_LOW) // Keeps it silent
                .setOngoing(true) // Prevents the child from swiping it away
                .build();
    }

    /**
     * Fires a high-priority alert (heads-up notification) when a child
     * tries to open a blocked app or access a forbidden website.
     */
    public static void showBlockAlert(Context context, String blockedItemName) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Access Blocked \uD83D\uDEE1\uFE0F")
                .setContentText("Attempted to access restricted content: " + blockedItemName)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Pops down from the top of the screen
                .setAutoCancel(true);

        manager.notify(ALERT_NOTIFICATION_ID, builder.build());
    }

    /**
     * Fires a warning when a child is approaching their daily screen time limit.
     */
    public static void showTimeWarning(Context context, String appName, int minutesLeft) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.CHANNEL_SCREEN_TIME)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Screen Time Warning")
                .setContentText("Only " + minutesLeft + " minutes remaining for " + appName + ".")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        // We use the app's hashcode as the ID so warnings for different apps don't
        // overwrite each other in the notification tray!
        manager.notify(appName.hashCode(), builder.build());
    }
}