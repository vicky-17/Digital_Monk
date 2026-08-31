package com.curbme.app.data.local.db.entity

import androidx.room.Entity

/**
 * Entity representing aggregated app usage for a specific day.
 * 
 * @property date The date in yyyy-MM-dd format.
 * @property packageName The unique package name of the app.
 * @property totalTime The total usage time in milliseconds for this day.
 * @property hourlyUsage A comma-separated string of 24 longs, representing milliseconds spent in each hour.
 * @property launchCount Number of times the app was launched on this day.
 * @property lastUsed The wall-clock timestamp (ms) of the last usage session.
 */
@Entity(tableName = "app_usage", primaryKeys = ["date", "packageName"])
data class AppUsageEntity(
    val date: String,
    val packageName: String,
    val totalTime: Long = 0,
    val hourlyUsage: String = "0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0",
    val launchCount: Int = 0,
    val lastUsed: Long = 0
)
