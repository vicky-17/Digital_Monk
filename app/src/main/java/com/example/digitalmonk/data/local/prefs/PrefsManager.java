package com.example.digitalmonk.data.local.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.digitalmonk.core.utils.Constants;

import java.util.HashSet;
import java.util.Set;

/**
 * Why we made this file:
 * A parental control app needs to remember many settings (like which apps are blocked,
 * the parent's PIN, and whether the VPN is active) even after the phone restarts.
 * * We use SharedPreferences to store these small pieces of data in a local file on
 * the device. This class acts as the "Settings Manager," providing a clean way for
 * the rest of the app to read and write these values without dealing with the
 * underlying Android storage APIs directly.
 *
 * What the file name defines:
 * "Prefs" stands for Preferences (user settings).
 * "Manager" identifies it as the central controller for saving and retrieving
 * those settings.
 */
public class PrefsManager {

    private final SharedPreferences prefs;

    // ── Keys ──────────────────────────────────────────────────────────────────
    private static final String KEY_PIN = "parent_pin";
    private static final String KEY_SETUP_COMPLETE = "setup_complete";
    private static final String KEY_BLOCK_SHORTS = "block_shorts";
    private static final String KEY_BLOCK_PORN = "block_porn";
    private static final String KEY_SAFE_SEARCH = "safe_search";
    private static final String KEY_BLOCKED_PACKAGES = "blocked_packages";
    private static final String KEY_SCREEN_TIME_LIMIT = "screen_time_limit";
    private static final String KEY_SCREEN_TIME_ENABLED = "screen_time_enabled";
    private static final String KEY_VPN_FILTER = "vpn_filter_enabled";
    private static final String KEY_KEEP_VPN_ALIVE = "keep_vpn_alive";
    private static final String KEY_PREVENT_VPN_OVERRIDE = "prevent_vpn_override";
    private static final String KEY_LAST_HEARTBEAT = "last_vpn_heartbeat_type";
    private static final String KEY_LAST_HEARTBEAT_TS = "last_vpn_heartbeat_ts";
    private static final String KEY_IS_PREMIUM = "is_premium";
    private static final String KEY_PREMIUM_EXPIRY = "premium_expiry";

    public PrefsManager(Context context) {
        this.prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── PIN ───────────────────────────────────────────────────────────────────

    public void savePin(String pin) {
        prefs.edit().putString(KEY_PIN, pin).apply();
    }

    public String getPin() {
        return prefs.getString(KEY_PIN, "");
    }

    public boolean hasPin() {
        String pin = prefs.getString(KEY_PIN, "");
        return pin != null && !pin.isEmpty();
    }

    public void clearPin() {
        prefs.edit().remove(KEY_PIN).apply();
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    public boolean isSetupComplete() {
        return prefs.getBoolean(KEY_SETUP_COMPLETE, false);
    }

    public void setSetupComplete(boolean value) {
        prefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply();
    }

    // ── Content Filters ───────────────────────────────────────────────────────

    public boolean isBlockShorts() {
        return prefs.getBoolean(KEY_BLOCK_SHORTS, true);
    }

    public void setBlockShorts(boolean value) {
        prefs.edit().putBoolean(KEY_BLOCK_SHORTS, value).apply();
    }

    public boolean isBlockPorn() {
        return prefs.getBoolean(KEY_BLOCK_PORN, false);
    }

    public void setBlockPorn(boolean value) {
        prefs.edit().putBoolean(KEY_BLOCK_PORN, value).apply();
    }

    public boolean isEnforceSafeSearch() {
        return prefs.getBoolean(KEY_SAFE_SEARCH, false);
    }

    public void setEnforceSafeSearch(boolean value) {
        prefs.edit().putBoolean(KEY_SAFE_SEARCH, value).apply();
    }

    // ── App Blocking ──────────────────────────────────────────────────────────

    public Set<String> getBlockedPackages() {
        return prefs.getStringSet(KEY_BLOCKED_PACKAGES, new HashSet<>());
    }

    public void saveBlockedPackages(Set<String> packages) {
        prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, packages).apply();
    }

    public boolean isAppBlocked(String packageName) {
        return getBlockedPackages().contains(packageName);
    }

    public void addBlockedPackage(String packageName) {
        Set<String> packages = new HashSet<>(getBlockedPackages());
        packages.add(packageName);
        saveBlockedPackages(packages);
    }

    public void removeBlockedPackage(String packageName) {
        Set<String> packages = new HashSet<>(getBlockedPackages());
        packages.remove(packageName);
        saveBlockedPackages(packages);
    }

    // ── Screen Time ───────────────────────────────────────────────────────────

    public int getDailyScreenTimeLimitMinutes() {
        return prefs.getInt(KEY_SCREEN_TIME_LIMIT, 0);
    }

    public void setDailyScreenTimeLimitMinutes(int value) {
        prefs.edit().putInt(KEY_SCREEN_TIME_LIMIT, value).apply();
    }

    public boolean isScreenTimeEnabled() {
        return prefs.getBoolean(KEY_SCREEN_TIME_ENABLED, false);
    }

    public void setScreenTimeEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_SCREEN_TIME_ENABLED, value).apply();
    }

    // ── VPN / DNS Filter ─────────────────────────────────────────────────────

    public boolean isVpnFilterEnabled() {
        return prefs.getBoolean(KEY_VPN_FILTER, false);
    }

    public void setVpnFilterEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_VPN_FILTER, value).apply();
    }

    // ── VPN Heartbeat ─────────────────────────────────────────────────────────

    public String getLastVpnHeartbeatType() {
        return prefs.getString(KEY_LAST_HEARTBEAT, "");
    }

    public void setLastVpnHeartbeatType(String value) {
        prefs.edit().putString(KEY_LAST_HEARTBEAT, value).apply();
    }

    public long getLastVpnHeartbeatTimestamp() {
        return prefs.getLong(KEY_LAST_HEARTBEAT_TS, 0L);
    }

    public void setLastVpnHeartbeatTimestamp(long value) {
        prefs.edit().putLong(KEY_LAST_HEARTBEAT_TS, value).apply();
    }

    // ── VPN Keep-Alive ────────────────────────────────────────────────────────

    public boolean isKeepVpnAlive() {
        return prefs.getBoolean(KEY_KEEP_VPN_ALIVE, true);
    }

    public void setKeepVpnAlive(boolean value) {
        prefs.edit().putBoolean(KEY_KEEP_VPN_ALIVE, value).apply();
    }

    // ── Prevent VPN Override ──────────────────────────────────────────────────

    public boolean isPreventVpnOverride() {
        return prefs.getBoolean(KEY_PREVENT_VPN_OVERRIDE, false);
    }

    public void setPreventVpnOverride(boolean value) {
        prefs.edit().putBoolean(KEY_PREVENT_VPN_OVERRIDE, value).apply();
    }

    // ── Premium ───────────────────────────────────────────────────────────────

    public boolean isPremium() {
        return prefs.getBoolean(KEY_IS_PREMIUM, false);
    }

    public void setPremium(boolean value) {
        prefs.edit().putBoolean(KEY_IS_PREMIUM, value).apply();
    }

    public long getPremiumExpiryEpoch() {
        return prefs.getLong(KEY_PREMIUM_EXPIRY, 0L);
    }

    public void setPremiumExpiryEpoch(long value) {
        prefs.edit().putLong(KEY_PREMIUM_EXPIRY, value).apply();
    }

    // Add to PrefsManager.java
    public boolean isSafeSearchEnabled() {
        return prefs.getBoolean("safe_search_enabled", false);
    }

    public void setSafeSearchEnabled(boolean value) {
        prefs.edit().putBoolean("safe_search_enabled", value).apply();
    }

    public boolean validatePin(String enteredPin) {
        String savedPin = getPin(); // Assumes getPin() already exists
        return enteredPin != null && enteredPin.equals(savedPin);
    }
    public boolean isYoutubeFilterEnabled() {
        return prefs.getBoolean("youtube_filter_enabled", false);
    }

    public void setYoutubeFilterEnabled(boolean value) {
        prefs.edit().putBoolean("youtube_filter_enabled", value).apply();
    }
}