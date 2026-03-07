package com.example.digitalmonk.core.utils

object Constants {

    // ── Logging ───────────────────────────────────────────────────────────────
    const val LOG_TAG = "DigitalMonk"

    // ── Notification IDs ──────────────────────────────────────────────────────
    const val NOTIFICATION_ID_GUARDIAN   = 1
    const val NOTIFICATION_ID_OVERLAY    = 2
    const val NOTIFICATION_ID_VPN        = 3
    const val NOTIFICATION_ID_SCREENTIME = 4

    // ── Notification Channels ─────────────────────────────────────────────────
    const val CHANNEL_GUARDIAN    = "channel_guardian"
    const val CHANNEL_OVERLAY     = "channel_overlay"
    const val CHANNEL_VPN         = "channel_vpn"
    const val CHANNEL_SCREEN_TIME = "channel_screen_time"
    const val CHANNEL_ALERTS      = "channel_alerts"

    // ── SharedPrefs / DataStore keys ──────────────────────────────────────────
    const val PREFS_NAME = "digital_monk_prefs"

    // ── Request Codes ─────────────────────────────────────────────────────────
    const val RC_OVERLAY_PERMISSION    = 1001
    const val RC_USAGE_STATS           = 1002
    const val RC_DEVICE_ADMIN          = 1003
    const val RC_VPN_PERMISSION        = 1004
    const val RC_NOTIFICATION_PERM     = 1005

    // ── WorkManager Tags ──────────────────────────────────────────────────────
    const val WORK_USAGE_SYNC   = "work_usage_sync"
    const val WORK_BLOCKLIST_UPDATE = "work_blocklist_update"

    // ── Deep-link / Intent extras ─────────────────────────────────────────────
    const val EXTRA_TARGET_SCREEN = "extra_target_screen"
    const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
}