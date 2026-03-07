package com.example.digitalmonk.data.model

/**
 * Domain model representing a rule applied to a single installed app.
 *
 * This is a pure Kotlin data class — no Android framework dependencies.
 * It maps 1-to-1 with AppRuleEntity (Room) via a mapper function.
 */
data class AppRule(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean = false,
    val blockShorts: Boolean = false,
    val dailyLimitMinutes: Int = 0,      // 0 = no limit
    val allowedTimeWindowStart: Int = 0, // minutes from midnight (e.g. 480 = 8:00 AM)
    val allowedTimeWindowEnd: Int = 0,   // 0 = no window restriction
    val isSystemApp: Boolean = false
)