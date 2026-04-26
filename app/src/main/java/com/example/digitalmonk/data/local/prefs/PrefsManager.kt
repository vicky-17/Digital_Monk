package com.example.digitalmonk.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.digitalmonk.core.utils.Constants

/**
 * Manages all app preferences via SharedPreferences.
 *
 * Organised by feature section. As features grow, consider migrating
 * individual sections to DataStore (see DataStoreManager for new features).
 *
 * NOTE: PIN is stored here for now. Before public release, migrate PIN
 * to SecurePrefs (EncryptedSharedPreferences).
 */
class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    // ── PIN ───────────────────────────────────────────────────────────────────

    fun savePin(pin: String) = prefs.edit().putString(KEY_PIN, pin).apply()
    fun getPin(): String = prefs.getString(KEY_PIN, "") ?: ""
    fun hasPin(): Boolean = prefs.getString(KEY_PIN, "").isNullOrEmpty().not()
    fun clearPin() = prefs.edit().remove(KEY_PIN).apply()

    // ── Setup ─────────────────────────────────────────────────────────────────

    var isSetupComplete: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    // ── Content Filters ───────────────────────────────────────────────────────

    var blockShorts: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_SHORTS, true)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_SHORTS, value).apply()

    var safeSearchEnabled: Boolean
        get() = prefs.getBoolean("safe_search_enabled", false)
        set(value) = prefs.edit().putBoolean("safe_search_enabled", value).apply()

    var blockPorn: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_PORN, false)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_PORN, value).apply()

    var enforceSafeSearch: Boolean
        get() = prefs.getBoolean(KEY_SAFE_SEARCH, false)
        set(value) = prefs.edit().putBoolean(KEY_SAFE_SEARCH, value).apply()

    // ── App Blocking ──────────────────────────────────────────────────────────

    fun getBlockedPackages(): Set<String> =
        prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()

    fun saveBlockedPackages(packages: Set<String>) =
        prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, packages).apply()

    fun isAppBlocked(packageName: String): Boolean =
        getBlockedPackages().contains(packageName)

    fun addBlockedPackage(packageName: String) {
        saveBlockedPackages(getBlockedPackages() + packageName)
    }

    fun removeBlockedPackage(packageName: String) {
        saveBlockedPackages(getBlockedPackages() - packageName)
    }

    // ── Screen Time ───────────────────────────────────────────────────────────

    var dailyScreenTimeLimitMinutes: Int
        get() = prefs.getInt(KEY_SCREEN_TIME_LIMIT, 0)    // 0 = no limit
        set(value) = prefs.edit().putInt(KEY_SCREEN_TIME_LIMIT, value).apply()

    var screenTimeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_TIME_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SCREEN_TIME_ENABLED, value).apply()

    // ── VPN / DNS Filter ─────────────────────────────────────────────────────

    var vpnFilterEnabled: Boolean
        get() = prefs.getBoolean(KEY_VPN_FILTER, false)
        set(value) = prefs.edit().putBoolean(KEY_VPN_FILTER, value).apply()

    // ── VPN Heartbeat (lightweight — no Room needed) ─────────────────────────
    //
    // Written by DnsVpnService every 7 min (ALIVE) and on clean stop (STOPPED).
    // Read by VpnHeartbeatMonitorWorker to detect unexpected kills.
    // When Room is fully wired, swap this for VpnHeartBeatDao.

    var lastVpnHeartbeatType: String
        get() = prefs.getString(KEY_LAST_HEARTBEAT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_HEARTBEAT, value).apply()

    var lastVpnHeartbeatTimestamp: Long
        get() = prefs.getLong(KEY_LAST_HEARTBEAT_TS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_HEARTBEAT_TS, value).apply()

    // ── VPN Keep-Alive ────────────────────────────────────────────────────────
    //
    // When ON: WatchdogService restarts DnsVpnService if it dies unexpectedly.
    // Screen-on BroadcastReceiver checks tunnel health on every wake.
    // WorkManager heartbeat watchdog runs every 15 min as a backup.
    //
    // Matches "Keep VPN alive" toggle in Detoxify's VPN Settings screen.

    var keepVpnAlive: Boolean
        get() = prefs.getBoolean(KEY_KEEP_VPN_ALIVE, true)   // ON by default — safer
        set(value) = prefs.edit().putBoolean(KEY_KEEP_VPN_ALIVE, value).apply()

    // ── Prevent VPN Override ──────────────────────────────────────────────────
    //
    // When ON: onRevoke() in DnsVpnService immediately re-requests VPN permission
    // and restarts the tunnel whenever another VPN tries to displace ours.
    // Requires PIN to disable (PIN-gate enforced in the UI toggle).
    //
    // Matches "Prevent VPN override" toggle in Detoxify's VPN Settings screen.

    var preventVpnOverride: Boolean
        get() = prefs.getBoolean(KEY_PREVENT_VPN_OVERRIDE, false)
        set(value) = prefs.edit().putBoolean(KEY_PREVENT_VPN_OVERRIDE, value).apply()

    // ── Subscription / Premium ────────────────────────────────────────────────
    // (billing server validates; we only cache the state locally)

    var isPremium: Boolean
        get() = prefs.getBoolean(KEY_IS_PREMIUM, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_PREMIUM, value).apply()

    var premiumExpiryEpoch: Long
        get() = prefs.getLong(KEY_PREMIUM_EXPIRY, 0L)
        set(value) = prefs.edit().putLong(KEY_PREMIUM_EXPIRY, value).apply()

    // ── Keys ──────────────────────────────────────────────────────────────────

    companion object {
        private const val KEY_PIN                  = "parent_pin"
        private const val KEY_SETUP_COMPLETE       = "setup_complete"
        private const val KEY_BLOCK_SHORTS         = "block_shorts"
        private const val KEY_BLOCK_PORN           = "block_porn"
        private const val KEY_SAFE_SEARCH          = "safe_search"
        private const val KEY_BLOCKED_PACKAGES     = "blocked_packages"
        private const val KEY_SCREEN_TIME_LIMIT    = "screen_time_limit"
        private const val KEY_SCREEN_TIME_ENABLED  = "screen_time_enabled"
        private const val KEY_VPN_FILTER           = "vpn_filter_enabled"
        private const val KEY_KEEP_VPN_ALIVE       = "keep_vpn_alive"
        private const val KEY_PREVENT_VPN_OVERRIDE = "prevent_vpn_override"
        private const val KEY_LAST_HEARTBEAT       = "last_vpn_heartbeat_type"
        private const val KEY_LAST_HEARTBEAT_TS    = "last_vpn_heartbeat_ts"
        private const val KEY_IS_PREMIUM           = "is_premium"
        private const val KEY_PREMIUM_EXPIRY       = "premium_expiry"
    }
}