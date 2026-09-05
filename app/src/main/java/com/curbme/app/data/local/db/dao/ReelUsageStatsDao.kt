package com.curbme.app.data.local.db.dao

import androidx.room.*
import com.curbme.app.data.local.db.entity.ReelUsageStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelUsageStatsDao {
    @Query("SELECT * FROM reel_usage_stats WHERE date = :date")
    suspend fun getForDate(date: String): List<ReelUsageStatsEntity>

    @Query("SELECT * FROM reel_usage_stats WHERE date = :date")
    fun getForDateFlow(date: String): Flow<List<ReelUsageStatsEntity>>

    @Upsert
    suspend fun upsert(entity: ReelUsageStatsEntity)

    @Query("UPDATE reel_usage_stats SET totalTime = totalTime + :deltaMs, lastVisited = :lastVisited WHERE date = :date AND packageName = :packageName")
    suspend fun addTime(date: String, packageName: String, deltaMs: Long, lastVisited: Long): Int

    @Query("UPDATE reel_usage_stats SET reelCount = reelCount + 1, lastVisited = :lastVisited WHERE date = :date AND packageName = :packageName")
    suspend fun incrementCount(date: String, packageName: String, lastVisited: Long): Int
}
