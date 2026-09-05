package com.curbme.app.data.models

enum class ShortsBlockMode {
    COMPLETE_BLOCK,      // Block shorts/reels across all target short-video apps
    SELECTIVE_APPS,      // Block shorts/reels only in user-selected target apps
    DAILY_TIME_LIMIT,    // Block after X minutes of total short video time today
    REEL_COUNT_LIMIT,    // Block after Y reels/shorts scrolled today
    SCHEDULED_WINDOWS    // Block outside allowed time windows
}

data class ReelPlanConfig(
    val mode: ShortsBlockMode = ShortsBlockMode.COMPLETE_BLOCK,
    val isDisplayReelCounterOverlay: Boolean = true,
    val enabledTargetApps: Set<String> = setOf(
        "com.google.android.youtube",
        "app.revanced.android.youtube",
        "com.instagram.android",
        "com.snapchat.android",
        "com.facebook.katana",
        "com.zhiliaoapp.musically"
    ),
    val dailyTimeLimitMinutes: Int = 15,
    val dailyReelCountLimit: Int = 20,
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 18,
    val endMinute: Int = 0
)
