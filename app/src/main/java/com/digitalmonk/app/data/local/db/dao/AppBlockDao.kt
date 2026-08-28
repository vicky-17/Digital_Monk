package com.digitalmonk.app.data.local.db.dao

import androidx.room.*
import com.digitalmonk.app.data.local.db.entity.AppBlockRule
import kotlinx.coroutines.flow.Flow

@Dao
interface AppBlockDao {
    @Query("SELECT * FROM app_block_rules")
    fun getAllRules(): Flow<List<AppBlockRule>>

    @Query("SELECT * FROM app_block_rules WHERE packageName = :packageName LIMIT 1")
    suspend fun getRuleForPackage(packageName: String): AppBlockRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AppBlockRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<AppBlockRule>)

    @Delete
    suspend fun deleteRule(rule: AppBlockRule)

    @Query("DELETE FROM app_block_rules WHERE packageName = :packageName")
    suspend fun deleteRuleByPackage(packageName: String)

    @Query("DELETE FROM app_block_rules WHERE planName = :planName")
    suspend fun deleteRulesByPlanName(planName: String)

    @Query("DELETE FROM app_block_rules")
    suspend fun deleteAllRules()
}