package com.example.digitalmonk.service.monitor;

/**
 * ProtectionIssue — Enum of every detectable protection gap.
 * Each constant maps to one specific thing that can go wrong.
 * The monitor returns a Set<ProtectionIssue> so multiple problems
 * can be reported simultaneously.
 * Priority field controls which issue is shown first when multiple
 * are present (lower number = higher priority).
 */
public enum ProtectionIssue {

    // ── Core permissions ──────────────────────────────────────────────────────

    /**
     * GuardianAccessibilityService is not enabled in system settings.
     * This breaks Shorts blocking and app blocking entirely.
     */
    ACCESSIBILITY_DISABLED(1, "Accessibility service is off"),

    /**
     * SYSTEM_ALERT_WINDOW permission is not granted.
     * Overlays and block screens cannot be shown.
     */
    OVERLAY_PERMISSION_MISSING(2, "Display-over-other-apps permission is missing"),

    /**
     * PACKAGE_USAGE_STATS is not granted.
     * Screen time tracking and SettingsAppMonitor both stop working.
     */
    USAGE_STATS_MISSING(3, "Usage access permission is missing"),

    /**
     * App is not exempt from battery optimization.
     * Android may kill background services at any time.
     */
    BATTERY_OPTIMIZATION_ACTIVE(4, "Battery optimization is not disabled"),

    // ── VPN / content filter issues ───────────────────────────────────────────

    /**
     * Parent has enabled VPN filtering in the app (isSafeSearchEnabled = true)
     * but DnsVpnService.isServiceRunning is false.
     * Cause: system killed the service, or it crashed.
     */
    VPN_SERVICE_DEAD(5, "VPN filter was killed by the system"),

    /**
     * Our VPN is running but the system-level Always-On VPN is NOT set to our
     * package. This means a reboot, network switch, or the child toggling the
     * system VPN toggle could silently disconnect us.
     */
    ALWAYS_ON_VPN_NOT_SET(6, "Always-On VPN is not locked to Digital Monk"),

    /**
     * Another VPN app is currently the active foreground VPN on the device.
     * Our VPN cannot run simultaneously — the child may be bypassing the filter.
     */
    ANOTHER_VPN_ACTIVE(7, "Another VPN app is overriding Digital Monk"),

    /**
     * VPN permission has been revoked (VpnService.prepare() returns non-null
     * even though we previously had it). Requires the parent to re-grant.
     */
    VPN_PERMISSION_REVOKED(8, "VPN permission was revoked");

    // ── Fields ────────────────────────────────────────────────────────────────

    /** Lower number = shown first when multiple issues exist. */
    public final int priority;

    /** Human-readable description for logging and future UI display. */
    public final String description;

    ProtectionIssue(int priority, String description) {
        this.priority = priority;
        this.description = description;
    }
}