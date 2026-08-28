package com.digitalmonk.app.service.vpn.heartbeat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object (DAO) for the VPN Heartbeat table.
 * Contains the SQL queries required by the Watchdog and VPN services.
 */
@Dao
interface VpnHeartBeatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(heartBeat: VpnHeartBeatEntity)

    @get:Query("SELECT * FROM vpn_heartbeat ORDER BY timestamp DESC LIMIT 1")
    val lastHeartBeat: VpnHeartBeatEntity?

    /** * Cleans up old records — keep only the latest 20 to avoid unbounded growth.
     */
    @Query("DELETE FROM vpn_heartbeat WHERE id NOT IN (SELECT id FROM vpn_heartbeat ORDER BY timestamp DESC LIMIT 20)")
    fun pruneOldRecords()
}