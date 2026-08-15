package com.digitalmonk.app.data.local.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.digitalmonk.app.data.local.db.dao.UsageLogDao;
import com.digitalmonk.app.service.vpn.heartbeat.VpnHeartBeatDao;
import com.digitalmonk.app.data.local.db.entity.UsageLogEntity;
import com.digitalmonk.app.service.vpn.heartbeat.VpnHeartBeatEntity;

/**
 * The sole Database class for the application.
 * All entities (UsageLogs, Heartbeats, etc.) are registered here.
 */
@Database(entities = {UsageLogEntity.class, VpnHeartBeatEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract UsageLogDao usageLogDao();
    public abstract VpnHeartBeatDao vpnHeartBeatDao();

    /**
     * Standard Singleton pattern to provide access to the database.
     */
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "app_database") // Generic name for the SQLite file
                            .fallbackToDestructiveMigrationOnDowngrade(false)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}