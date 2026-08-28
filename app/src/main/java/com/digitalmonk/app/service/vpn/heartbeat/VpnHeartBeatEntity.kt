package com.digitalmonk.app.service.vpn.heartbeat

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * Why we made this file:
 * This Room Entity defines the SQLite table structure for the VPN heartbeat log.
 * By writing "ALIVE" every 7 minutes, the external Watchdog worker can query this
 * table and know if the Android OS silently killed the VPN. If the last record
 * isn't "ALIVE" or is too old, the Watchdog knows it needs to revive the VPN.
 */
@Entity(tableName = "vpn_heartbeat")
class VpnHeartBeatEntity {
    // ── Getters and Setters Required by Room ──────────────────────────────────
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    var type: String? = null
    var timestamp: Long = 0

    /**
     * Default Constructor for Room.
     */
    constructor()

    /**
     * Convenience constructor mimicking Kotlin's default timestamp parameter.
     */
    @Ignore
    constructor(type: String?) {
        this.type = type
        this.timestamp = System.currentTimeMillis()
    }

    companion object {
        const val TYPE_ALIVE: String = "ALIVE"
        const val TYPE_STOPPED: String = "STOPPED"
    }
}