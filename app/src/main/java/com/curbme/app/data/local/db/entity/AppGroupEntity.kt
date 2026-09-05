package com.curbme.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a group of applications sharing a common time budget limit or schedule.
 */
@Entity(tableName = "app_groups")
data class AppGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val packagesCsv: String = "",
    val dailyLimitMinutes: Long = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
