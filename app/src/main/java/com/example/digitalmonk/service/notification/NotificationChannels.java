package com.example.digitalmonk.service.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import com.example.digitalmonk.core.utils.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * Why we made this file:
 * Since Android 8.0 (Oreo), all notifications must be assigned to a specific "Channel".
 * For a parental control application, categorizing these notifications is essential
 * so the system (and the parent) knows which alerts are silent background trackers
 * (like the VPN) and which are urgent (like bypass attempts).
 *
 * This utility class creates those channels when the application first starts.
 */
public class NotificationChannels {

    /**
     * Private constructor for Utility Class.
     */
    private NotificationChannels() {}

    /**
     * Creates all notification channels at app startup.
     * Must be called from Application.onCreate() before any notification is posted.
     */
    public static void createAll(Context context) {
        // Notification channels are only required on Android O (API 26) and above.
        // It is a standard Java/Android practice to guard channel creation with this check.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            List<NotificationChannel> channels = new ArrayList<>();

            // 1. Guardian Service Channel (Silent/Low Importance)
            NotificationChannel guardianChannel = new NotificationChannel(
                    Constants.CHANNEL_GUARDIAN,
                    "Guardian Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            guardianChannel.setDescription("Digital Monk is actively monitoring the device.");
            channels.add(guardianChannel);

            // 2. VPN / Content Filter Channel (Silent/Low Importance)
            NotificationChannel vpnChannel = new NotificationChannel(
                    Constants.CHANNEL_VPN,
                    "Content Filter",
                    NotificationManager.IMPORTANCE_LOW
            );
            vpnChannel.setDescription("DNS-based content filtering is active.");
            channels.add(vpnChannel);

            // 3. Screen Time Channel (Default Importance - makes a sound)
            NotificationChannel screenTimeChannel = new NotificationChannel(
                    Constants.CHANNEL_SCREEN_TIME,
                    "Screen Time",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            screenTimeChannel.setDescription("Screen time limit warnings and summaries.");
            channels.add(screenTimeChannel);

            // 4. Alerts Channel (High Importance - pops up on screen)
            NotificationChannel alertsChannel = new NotificationChannel(
                    Constants.CHANNEL_ALERTS,
                    "Parental Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alertsChannel.setDescription("Alerts when a child tries to access blocked content.");
            channels.add(alertsChannel);

            // Register all channels at once
            manager.createNotificationChannels(channels);
        }
    }
}