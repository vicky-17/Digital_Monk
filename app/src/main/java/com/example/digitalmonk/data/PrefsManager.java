package com.example.digitalmonk.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class PrefsManager {
    private static final String PREF_NAME = "G4Prefs";
    private SharedPreferences prefs;
    private Gson gson = new Gson();

    public PrefsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        initializeDefaults();
    }

    private void initializeDefaults() {
        if (!prefs.contains("liveTracking")) {
            prefs.edit()
                    .putBoolean("liveTracking", false)
                    .putBoolean("uninstallProtection", true)
                    .putBoolean("blockShorts", true)
                    .apply();
        }
    }

    // --- DEVICE ID ---
    public void saveDeviceId(String id) {
        prefs.edit().putString("deviceId", id).putBoolean("isPaired", true).apply();
    }
    public String getDeviceId() { return prefs.getString("deviceId", null); }
    public boolean isPaired() { return prefs.getBoolean("isPaired", false); }

    // --- SYNC SETTINGS ---
    public void saveSettings(long locInterval, long appInterval, long lastModified) {
        prefs.edit()
                .putLong("locationInterval", locInterval)
                .putLong("appSyncInterval", appInterval)
                .putLong("lastModified", lastModified)
                .apply();
    }

    public long getLocationInterval() { return prefs.getLong("locationInterval", 60000); }
    public long getAppSyncInterval() { return prefs.getLong("appSyncInterval", 120000); } // Default 2 mins
    public long getLastModified() { return prefs.getLong("lastModified", 0); }

    // --- RULE STORAGE ---
    public void saveAppRules(List<AppRule> rules) {
        String json = gson.toJson(rules);
        prefs.edit().putString("appRulesJson", json).apply();
    }

    public List<AppRule> getAppRules() {
        String json = prefs.getString("appRulesJson", "[]");
        Type type = new TypeToken<ArrayList<AppRule>>(){}.getType();
        return gson.fromJson(json, type);
    }

    // --- LAST SENT LOCATION (For Smart Triggers) ---
    public void saveLastSentLocation(double latitude, double longitude) {
        prefs.edit()
                .putString("lastSentLat", String.valueOf(latitude))
                .putString("lastSentLng", String.valueOf(longitude))
                .apply();
    }

    public double getLastSentLatitude() {
        String lat = prefs.getString("lastSentLat", null);
        return lat != null ? Double.parseDouble(lat) : 0.0;
    }

    public double getLastSentLongitude() {
        String lng = prefs.getString("lastSentLng", null);
        return lng != null ? Double.parseDouble(lng) : 0.0;
    }

    public boolean hasLastSentLocation() {
        return prefs.contains("lastSentLat") && prefs.contains("lastSentLng");
    }

    // --- BLOCKED APPS STORAGE ---
    // We use a Set for O(1) fast lookup in the accessibility service
    public void saveBlockedPackages(List<String> packageNames) {
        Set<String> set = new HashSet<>(packageNames);
        prefs.edit().putStringSet("blocked_packages_set", set).apply();
    }

    public boolean isAppBlocked(String packageName) {
        Set<String> set = prefs.getStringSet("blocked_packages_set", new HashSet<>());
        return set.contains(packageName);
    }

    // --- BLOCKED LISTS (Legacy - Keep if needed elsewhere) ---
    public void saveBlockedLists(List<String> apps, List<String> urls) {
        String appsJson = gson.toJson(apps);
        String urlsJson = gson.toJson(urls);

        prefs.edit()
                .putString("blockedApps", appsJson)
                .putString("blockedUrls", urlsJson)
                .apply();
    }

    public List<String> getBlockedApps() {
        String json = prefs.getString("blockedApps", "[]");
        Type type = new TypeToken<ArrayList<String>>(){}.getType();
        return gson.fromJson(json, type);
    }

    public List<String> getBlockedUrls() {
        String json = prefs.getString("blockedUrls", "[]");
        Type type = new TypeToken<ArrayList<String>>(){}.getType();
        return gson.fromJson(json, type);
    }

    public boolean hasBlockedLists() {
        return prefs.contains("blockedApps") && prefs.contains("blockedUrls");
    }



    // ==========================================
    // --- GLOBAL SETTINGS (Web Dashboard Sync) ---
    // ==========================================

    public void saveGlobalSettings(boolean liveTracking, boolean uninstallProtection, boolean blockShorts) {
        prefs.edit()
                .putBoolean("liveTracking", liveTracking)
                .putBoolean("uninstallProtection", uninstallProtection)
                .putBoolean("blockShorts", blockShorts)
                .apply();
    }

    public boolean isLiveTrackingEnabled() {
        // Defaults to true so it works immediately before the first sync
        return prefs.getBoolean("liveTracking", false);
    }

    public boolean isUninstallProtectionEnabled() {
        // Defaults to false so the parent doesn't get locked out by accident
        return prefs.getBoolean("uninstallProtection", true);
    }

    public boolean isBlockShortsEnabled() {
        return prefs.getBoolean("blockShorts", true);
    }


    public void saveFcmToken(String token) {
        prefs.edit().putString("fcmToken", token).apply();
    }
    public String getFcmToken() {
        return prefs.getString("fcmToken", null);
    }


}