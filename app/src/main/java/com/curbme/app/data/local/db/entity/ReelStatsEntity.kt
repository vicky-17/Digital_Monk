package com.curbme.app.data.local.db.entity

import androidx.room.Entity

/**
 * Stores aggregated short video / reel scroll counts per date.
 */
@Entity(tableName = "reel_stats", primaryKeys = ["date"])
data class ReelStatsEntity(
    val date: String,
    val count: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
