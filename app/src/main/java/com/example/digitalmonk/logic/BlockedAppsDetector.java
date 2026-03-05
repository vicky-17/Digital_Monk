package com.example.digitalmonk.logic;

import com.example.digitalmonk.data.PrefsManager;

public class BlockedAppsDetector {
    private PrefsManager prefs;

    public BlockedAppsDetector(PrefsManager prefs) {
        this.prefs = prefs;
    }

    public boolean shouldBlockApp(String packageName) {
        // Checks if the current app is in the blocked list
        return prefs != null && prefs.isAppBlocked(packageName);
    }
}