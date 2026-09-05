package com.curbme.app.data.local.db.entity

import androidx.room.Entity

/**
 * Stores active time spent watching short videos / reels per package and date.
 */
@Entity(tableName = "reel_usage_stats", primaryKeys = ["date", "packageName"])
data class ReelUsageStatsEntity(
    val date: String,
    val packageName: String,
    val totalTime: Long = 0,
    val reelCount: Int = 0,
    val lastVisited: Long = System.currentTimeMillis()
)
