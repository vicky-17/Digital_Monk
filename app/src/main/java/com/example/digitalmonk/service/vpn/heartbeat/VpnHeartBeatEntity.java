package com.example.digitalmonk.service.vpn.heartbeat;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Why we made this file:
 * This Room Entity defines the SQLite table structure for the VPN heartbeat log.
 * By writing "ALIVE" every 7 minutes, the external Watchdog worker can query this
 * table and know if the Android OS silently killed the VPN. If the last record
 * isn't "ALIVE" or is too old, the Watchdog knows it needs to revive the VPN.
 */
@Entity(tableName = "vpn_heartbeat")
public class VpnHeartBeatEntity {

    public static final String TYPE_ALIVE = "ALIVE";
    public static final String TYPE_STOPPED = "STOPPED";

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String type;
    private long timestamp;

    /**
     * Default Constructor for Room.
     */
    public VpnHeartBeatEntity() {}

    /**
     * Convenience constructor mimicking Kotlin's default timestamp parameter.
     */
    public VpnHeartBeatEntity(String type) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Full constructor.
     */
    public VpnHeartBeatEntity(String type, long timestamp) {
        this.type = type;
        this.timestamp = timestamp;
    }

    public boolean isAlive() {
        return TYPE_ALIVE.equals(type);
    }

    // ── Getters and Setters Required by Room ──────────────────────────────────

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}