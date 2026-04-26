package com.example.digitalmonk.data.model;

/**
 * Why we made this file:
 * This Domain Model (POJO) represents a record of how much time the child
 * spent on a specific app. Instead of just passing around unstructured strings
 * and integers, we bundle them into this object.
 *
 * This structured format is highly important for your specific project because
 * this is exactly the type of data you will be serializing into JSON (using a
 * library like Gson or Moshi) to send via POST requests to your Vercel web app
 * and store in MongoDB.
 *
 * What the file name defines:
 * "Usage" refers to app activity or screen time.
 * "Log" indicates this is a recorded entry of that activity.
 */
public class UsageLog {

    private final String packageName;
    private final int minutes;

    /**
     * Default constructor initializing with an empty string and 0 minutes.
     * This replicates the Kotlin default parameters.
     * Frameworks like Firebase or Gson often require a default, no-argument
     * constructor to deserialize data properly.
     */
    public UsageLog() {
        this.packageName = "";
        this.minutes = 0;
    }

    /**
     * Full constructor to set specific usage log data.
     */
    public UsageLog(String packageName, int minutes) {
        this.packageName = packageName;
        this.minutes = minutes;
    }

    // Getters for immutability

    public String getPackageName() {
        return packageName;
    }

    public int getMinutes() {
        return minutes;
    }
}