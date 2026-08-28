package com.digitalmonk.app.data.local.db.dao

import androidx.room.*
import com.digitalmonk.app.data.local.db.entity.AppUsageEntity

@Dao
interface AppUsageDao {
    @Query("SELECT * FROM app_usage WHERE date = :date AND packageName = :packageName")
    suspend fun get(date: String, packageName: String): AppUsageEntity?

    @Query("SELECT * FROM app_usage WHERE date = :date")
    suspend fun getForDate(date: String): List<AppUsageEntity>

    @Query("SELECT * FROM app_usage WHERE date IN (:dates)")
    suspend fun getForDates(dates: List<String>): List<AppUsageEntity>

    @Upsert
    suspend fun upsert(usage: AppUsageEntity)

    @Query("SELECT MIN(lastUsed) FROM app_usage")
    suspend fun earliestTimestamp(): Long?

    @Query("DELETE FROM app_usage WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)
}
