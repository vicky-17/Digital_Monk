package com.curbme.app.data.local.db.dao

import androidx.room.*
import com.curbme.app.data.local.db.entity.AppGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppGroupDao {
    @Query("SELECT * FROM app_groups")
    suspend fun getAll(): List<AppGroupEntity>

    @Query("SELECT * FROM app_groups WHERE isActive = 1")
    fun getAllActiveFlow(): Flow<List<AppGroupEntity>>

    @Query("SELECT * FROM app_groups WHERE id = :groupId")
    suspend fun getById(groupId: String): AppGroupEntity?

    @Upsert
    suspend fun upsert(entity: AppGroupEntity)

    @Delete
    suspend fun delete(entity: AppGroupEntity)
}
