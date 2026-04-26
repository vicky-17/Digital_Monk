package com.example.digitalmonk.service.vpn.heartbeat

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

// ── Entity ────────────────────────────────────────────────────────────────────

/**
 * Persists a heartbeat record every 7 minutes while DnsVpnService is running.
 * On clean shutdown, a STOPPED record is written so the watchdog knows it was intentional.
 *
 * Pattern borrowed from DDG's VpnServiceHeartbeat.kt:
 *   app-tracking-protection/vpn-impl/src/main/java/com/duckduckgo/mobile/android/vpn/heartbeat/
 */
@Entity(tableName = "vpn_heartbeat")
data class VpnHeartBeatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,           // ALIVE or STOPPED
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isAlive(): Boolean = type == TYPE_ALIVE

    companion object {
        const val TYPE_ALIVE   = "ALIVE"
        const val TYPE_STOPPED = "STOPPED"
    }
}

// ── DAO ───────────────────────────────────────────────────────────────────────

@Dao
interface VpnHeartBeatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(heartBeat: VpnHeartBeatEntity)

    /**
     * Returns the most recent heartbeat record, or null if none exist.
     * Used by VpnMonitorService.onDestroy() and VpnHeartbeatMonitorWorker
     * to decide whether to restart the VPN.
     */
    @Query("SELECT * FROM vpn_heartbeat ORDER BY timestamp DESC LIMIT 1")
    fun getLastHeartBeat(): VpnHeartBeatEntity?

    /** Cleans up old records — keep only the latest 20 to avoid unbounded growth. */
    @Query("DELETE FROM vpn_heartbeat WHERE id NOT IN (SELECT id FROM vpn_heartbeat ORDER BY timestamp DESC LIMIT 20)")
    fun pruneOldRecords()
}