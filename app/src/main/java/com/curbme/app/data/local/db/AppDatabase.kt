package com.curbme.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import com.curbme.app.data.local.db.dao.AppBlockDao
import com.curbme.app.data.local.db.dao.AppUsageDao
import com.curbme.app.data.local.db.dao.UsageLogDao
import com.curbme.app.data.local.db.entity.AppBlockRule
import com.curbme.app.data.local.db.entity.AppUsageEntity
import com.curbme.app.data.local.db.entity.UsageLogEntity
import com.curbme.app.service.vpn.heartbeat.VpnHeartBeatDao
import com.curbme.app.service.vpn.heartbeat.VpnHeartBeatEntity
import kotlin.concurrent.Volatile

/**
 * The sole Database class for the application.
 * All entities (UsageLogs, Heartbeats, etc.) are registered here.
 */
@Database(
    entities = [UsageLogEntity::class, VpnHeartBeatEntity::class, AppBlockRule::class, AppUsageEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageLogDao(): UsageLogDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun vpnHeartBeatDao(): VpnHeartBeatDao
    abstract fun appBlockDao(): AppBlockDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Standard Singleton pattern to provide access to the database.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(AppDatabase::class.java) {
                INSTANCE ?: databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database",
                )
                    .enableMultiInstanceInvalidation()
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { INSTANCE = it }
            }
        }

        /**
         * Emergency utility to wipe the database files from disk.
         * Used to fix "Integrity Hash" crashes without uninstallation.
         */
        fun deleteDatabaseFile(context: Context) {
            INSTANCE?.close()
            INSTANCE = null
            context.deleteDatabase("app_database")
        }
    }
}
