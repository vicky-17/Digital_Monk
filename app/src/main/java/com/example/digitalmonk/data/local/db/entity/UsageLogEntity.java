package com.example.digitalmonk.data.local.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "usage_logs") // Professional practice to name the table explicitly
public class UsageLogEntity {

    @PrimaryKey(autoGenerate = true) // Room assigns unique IDs automatically
    private long id;

    private String packageName;
    private long timestamp;
    private long duration;

    public UsageLogEntity() { }

    public UsageLogEntity(String packageName, long duration) {
        this.packageName = packageName;
        this.duration = duration;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters are required for Room to access private fields
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
}