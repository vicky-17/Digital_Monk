package com.curbme.app.data.local.db.dao

import androidx.room.*
import com.curbme.app.data.local.db.entity.WebsiteStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebsiteStatsDao {
    @Query("SELECT * FROM website_stats WHERE date = :date")
    suspend fun getForDate(date: String): List<WebsiteStatsEntity>

    @Query("SELECT * FROM website_stats WHERE date = :date")
    fun getForDateFlow(date: String): Flow<List<WebsiteStatsEntity>>

    @Upsert
    suspend fun upsert(entity: WebsiteStatsEntity)

    @Query("UPDATE website_stats SET totalTime = totalTime + :deltaMs, lastVisited = :lastVisited WHERE date = :date AND packageName = :packageName AND domain = :domain")
    suspend fun addTime(date: String, packageName: String, domain: String, deltaMs: Long, lastVisited: Long): Int

    @Query("DELETE FROM website_stats WHERE date < :beforeDate")
    suspend fun purgeOlderThan(beforeDate: String)
}
