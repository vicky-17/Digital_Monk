package com.example.digitalmonk.data.model;

/**
 * Why we made this file:
 * This is a "Domain Model" or "POJO" (Plain Old Java Object). In clean architecture,
 * we separate how data is stored (Database Entities) from how the business logic
 * uses it.
 *
 * This file represents a single app on the child's phone and the specific
 * restrictions applied to it—such as whether it's fully blocked, if it has
 * a daily time limit, or if it's a system app that should never be restricted.
 *
 * What the file name defines:
 * "AppRule" defines the specific logic and constraints (the "Rules")
 * governing the usage of an application.
 */
public class AppRule {

    private final String packageName;
    private final String appName;
    private final boolean isBlocked;
    private final boolean blockShorts;
    private final int dailyLimitMinutes;
    private final int allowedTimeWindowStart;
    private final int allowedTimeWindowEnd;
    private final boolean isSystemApp;

    /**
     * Full constructor to initialize the immutable app rule.
     */
    public AppRule(
            String packageName,
            String appName,
            boolean isBlocked,
            boolean blockShorts,
            int dailyLimitMinutes,
            int allowedTimeWindowStart,
            int allowedTimeWindowEnd,
            boolean isSystemApp
    ) {
        this.packageName = packageName;
        this.appName = appName;
        this.isBlocked = isBlocked;
        this.blockShorts = blockShorts;
        this.dailyLimitMinutes = dailyLimitMinutes;
        this.allowedTimeWindowStart = allowedTimeWindowStart;
        this.allowedTimeWindowEnd = allowedTimeWindowEnd;
        this.isSystemApp = isSystemApp;
    }

    // Getters for immutability
    public String getPackageName() { return packageName; }
    public String getAppName() { return appName; }
    public boolean isBlocked() { return isBlocked; }
    public boolean isBlockShorts() { return blockShorts; }
    public int getDailyLimitMinutes() { return dailyLimitMinutes; }
    public int getAllowedTimeWindowStart() { return allowedTimeWindowStart; }
    public int getAllowedTimeWindowEnd() { return allowedTimeWindowEnd; }
    public boolean isSystemApp() { return isSystemApp; }
}