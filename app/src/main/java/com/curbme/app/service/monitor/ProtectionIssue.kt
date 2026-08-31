package com.curbme.app.service.monitor

enum class ProtectionIssue(@JvmField val priority: Int, description: String) {
    ACCESSIBILITY_DISABLED(1, "Accessibility service is off"),
    OVERLAY_PERMISSION_MISSING(2, "Display-over-other-apps permission is missing"),
    USAGE_STATS_MISSING(3, "Usage access permission is missing"),
    BATTERY_OPTIMIZATION_ACTIVE(4, "Battery optimization is not disabled"),
    VPN_PERMISSION_REVOKED(5, "VPN permission was revoked"),
    VPN_SERVICE_DEAD(6, "VPN filter was killed by the system"),
    ANOTHER_VPN_ACTIVE(7, "Another VPN app is overriding Digital Monk"),
    ALWAYS_ON_VPN_NOT_SET(8, "Always-On VPN is not locked to Digital Monk");

    /** Human-readable description for logging and future UI display.  */
    val description: String?

    init {
        this.description = description
    }
}