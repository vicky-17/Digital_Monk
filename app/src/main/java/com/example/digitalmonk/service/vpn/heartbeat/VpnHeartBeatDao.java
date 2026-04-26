package com.example.digitalmonk.service.vpn.heartbeat;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/**
 * Data Access Object (DAO) for the VPN Heartbeat table.
 * Contains the SQL queries required by the Watchdog and VPN services.
 */
@Dao
public interface VpnHeartBeatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(VpnHeartBeatEntity heartBeat);

    /**
     * Returns the most recent heartbeat record, or null if none exist.
     * Used by VpnMonitorService.onDestroy() and VpnHeartbeatMonitorWorker
     * to decide whether to restart the VPN.
     */
    @Query("SELECT * FROM vpn_heartbeat ORDER BY timestamp DESC LIMIT 1")
    VpnHeartBeatEntity getLastHeartBeat();

    /** * Cleans up old records — keep only the latest 20 to avoid unbounded growth.
     */
    @Query("DELETE FROM vpn_heartbeat WHERE id NOT IN (SELECT id FROM vpn_heartbeat ORDER BY timestamp DESC LIMIT 20)")
    void pruneOldRecords();
}