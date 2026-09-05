package com.curbme.app.data.local.db.dao

import androidx.room.*
import com.curbme.app.data.local.db.entity.ReelStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelStatsDao {
    @Query("SELECT count FROM reel_stats WHERE date = :date")
    suspend fun getCount(date: String): Int?

    @Query("SELECT count FROM reel_stats WHERE date = :date")
    fun getCountFlow(date: String): Flow<Int?>

    @Query("SELECT * FROM reel_stats WHERE date = :date")
    suspend fun getForDate(date: String): ReelStatsEntity?

    @Upsert
    suspend fun upsert(entity: ReelStatsEntity)

    @Query("DELETE FROM reel_stats WHERE date < :beforeDate")
    suspend fun purgeOlderThan(beforeDate: String)
}
