package com.example.digitalmonk.core.utils;

/**
 * Why we made this file:
 * In a complex system like Digital Monk, many different parts of the app
 * (Services, Receivers, and UI) need to refer to the same specific values,
 * such as Notification IDs or Preference keys.
 *
 * Instead of "hard-coding" these values (writing "digital_monk_prefs" in five
 * different files), we store them here. This creates a "Single Source of Truth."
 * If you ever need to change a channel ID or a request code, you only change it
 * here once, and the entire app updates automatically.
 *
 * What the file name defines:
 * "Constants" signifies that this class holds immutable (unchanging) values
 * that are used globally across the project.
 */
public final class Constants {

    // Suppress default constructor to prevent instantiation
    private Constants() {}

    // ── Logging ───────────────────────────────────────────────────────────────
    public static final String LOG_TAG = "DigitalMonk";

    // ── Notification IDs ──────────────────────────────────────────────────────
    public static final int NOTIFICATION_ID_GUARDIAN = 1;
    public static final int NOTIFICATION_ID_VPN = 3;


    // ── Notification Channels ─────────────────────────────────────────────────
    public static final String CHANNEL_GUARDIAN = "channel_guardian";
    public static final String CHANNEL_OVERLAY = "channel_overlay";
    public static final String CHANNEL_VPN = "channel_vpn";
    public static final String CHANNEL_SCREEN_TIME = "channel_screen_time";
    public static final String CHANNEL_ALERTS = "channel_alerts";
    public static final String CHANNEL_SILENT = "channel_silent";

    public static final int NOTIFICATION_ID_SETTINGS_BLOCK = 1004;


    // ── SharedPrefs / DataStore keys ──────────────────────────────────────────
    public static final String PREFS_NAME = "digital_monk_prefs";

    // ── Request Codes ─────────────────────────────────────────────────────────
    public static final int RC_OVERLAY_PERMISSION = 1001;
    public static final int RC_USAGE_STATS = 1002;
    public static final int RC_DEVICE_ADMIN = 1003;
    public static final int RC_VPN_PERMISSION = 1004;
    public static final int RC_NOTIFICATION_PERM = 1005;

    // ── WorkManager Tags ──────────────────────────────────────────────────────
    public static final String WORK_USAGE_SYNC = "work_usage_sync";
    public static final String WORK_BLOCKLIST_UPDATE = "work_blocklist_update";

    // ── Deep-link / Intent extras ─────────────────────────────────────────────
    public static final String EXTRA_TARGET_SCREEN = "extra_target_screen";
    public static final String EXTRA_BLOCKED_PACKAGE = "extra_blocked_package";


}