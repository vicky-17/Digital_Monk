package com.digitalmonk.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_logs")
data class UsageLogEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var packageName: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var duration: Long = 0,
)
