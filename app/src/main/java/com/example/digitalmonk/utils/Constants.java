package com.example.digitalmonk.utils;

public class Constants {
    // 🌍 CENTRAL SERVER URL (Dynamic - Can be updated at runtime)
    // Change this ONE place to update it everywhere
    public static String BASE_URL = "https://fsadmin-2-c3d183978587.herokuapp.com";

    // 🔗 API ENDPOINTS
    // We build these dynamically using BASE_URL so they never break
    public static final String API_BASE_URL = BASE_URL + "/api";
    public static final String PAIRING_URL = API_BASE_URL + "/devices/pair";
    public static final String LOCATION_URL = API_BASE_URL + "/location";
    public static final String APPS_URL = API_BASE_URL + "/apps";
    public static final String SETTINGS_URL = API_BASE_URL + "/settings";

    // ⚙️ APP SETTINGS
    public static final String LOG_TAG = "G4Monitor";
    public static final int NOTIFICATION_ID = 1;
    public static final String NOTIFICATION_CHANNEL_ID = "location_channel";
}