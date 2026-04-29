package com.example.digitalmonk.core.utils;

import android.content.Context;

import com.example.digitalmonk.service.accessibility.GuardianAccessibilityService;

/**
 * Detects TWO failure modes:
 * 1. Permission not granted at all.
 * 2. Permission granted but service is frozen (no events received recently).
 */
public class AccessibilityHealthChecker {

    // How long without an event before we consider the service "frozen"
    private static final long STALE_THRESHOLD_MS = 15_000L; // 15 seconds

    public enum HealthStatus {
        HEALTHY,           // Granted + receiving events
        NOT_GRANTED,       // Not in enabled services list
        FROZEN             // Granted but no events recently (malfunction)
    }

    private AccessibilityHealthChecker() {}

    public static HealthStatus check(Context context) {
        boolean isGranted = PermissionHelper.isAccessibilityEnabled(context);

        if (!isGranted) {
            return HealthStatus.NOT_GRANTED;
        }

        // Service is listed as enabled — now check if it's actually alive
        long lastEvent = GuardianAccessibilityService.lastEventTimestamp;
        long now = System.currentTimeMillis();

        // If the app just started (lastEvent == 0), give a grace period of 20s
        // before declaring it frozen, so we don't false-alarm on cold start.
        if (lastEvent == 0) {
            long appStart = GuardianAccessibilityService.serviceConnectedTimestamp;
            if (appStart == 0 || (now - appStart) < 20_000L) {
                return HealthStatus.HEALTHY; // Grace period
            }
            return HealthStatus.FROZEN;
        }

        if ((now - lastEvent) > STALE_THRESHOLD_MS) {
            return HealthStatus.FROZEN;
        }

        return HealthStatus.HEALTHY;
    }

    public static boolean needsLockdown(Context context) {
        HealthStatus s = check(context);
        return s == HealthStatus.NOT_GRANTED || s == HealthStatus.FROZEN;
    }

    public static boolean isFrozen(Context context) {
        return check(context) == HealthStatus.FROZEN;
    }
}