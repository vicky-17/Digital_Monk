package com.curbme.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.curbme.app.data.local.db.entity.UsageLogEntity

@Dao
interface UsageLogDao {
    @Insert
    fun insert(log: UsageLogEntity)

    @Query("SELECT * FROM usage_logs ORDER BY timestamp DESC")
    fun getAllLogs(): List<UsageLogEntity>

    @Query("DELETE FROM usage_logs")
    fun deleteAll()
}