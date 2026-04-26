package com.example.digitalmonk.data.model;

/**
 * Why we made this file:
 * This Domain Model (POJO) encapsulates the screen time limit configuration.
 * While we could just pass around an `int` everywhere in the app, wrapping it
 * in a dedicated class provides stronger type safety. It ensures that a developer
 * doesn't accidentally pass a "daily limit" integer into a method expecting
 * a "allowed time window start" integer.
 *
 * What the file name defines:
 * "ScreenTimeLimit" clearly states that this class represents the maximum
 * allowed usage time for the child's device.
 */
public class ScreenTimeLimit {

    private final int minutesPerDay;

    /**
     * Default constructor initializing with 0 minutes (which represents no limit).
     * This replicates the Kotlin default parameter: `val minutesPerDay: Int = 0`.
     */
    public ScreenTimeLimit() {
        this.minutesPerDay = 0;
    }

    /**
     * Full constructor to set a specific daily time limit.
     */
    public ScreenTimeLimit(int minutesPerDay) {
        this.minutesPerDay = minutesPerDay;
    }

    // Getter for immutability

    public int getMinutesPerDay() {
        return minutesPerDay;
    }
}