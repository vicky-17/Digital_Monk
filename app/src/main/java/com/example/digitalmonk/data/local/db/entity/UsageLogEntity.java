package com.example.digitalmonk.data.local.db.entity;

/**
 * Why we made this file:
 * This class represents a "Database Entity." Since Digital Monk needs to track
 * how long a child spends on specific apps, we must store that data locally
 * on the phone before syncing it to the web interface.
 *
 * In a professional Android app using the Room persistence library, this Java
 * class defines the structure of a table in your SQLite database. Each instance
 * of this class represents one row in the "UsageLog" table.
 *
 * What the file name defines:
 * "UsageLog" describes the data being stored (records of app activity).
 * "Entity" is a standard architectural term for a class that maps directly
 * to a database table.
 */
public class UsageLogEntity {

    private long id;

    // Default constructor required for many Java frameworks and libraries
    public UsageLogEntity() {
        this.id = 0L;
    }

    public UsageLogEntity(long id) {
        this.id = id;
    }

    // Getter and Setter methods
    // In Java, we use these to follow the principle of "Encapsulation"
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}