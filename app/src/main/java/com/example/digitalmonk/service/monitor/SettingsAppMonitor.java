package com.example.digitalmonk.service.monitor;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * SettingsAppMonitor
 * ─────────────────────────────────────────────────────────────────────────────
 * Detects when the user navigates INTO or OUT OF a settings app using
 * UsageStatsManager — NO accessibility required.
 *
 * How it works:
 *   1. Queries UsageEvents for the last 3 seconds every 300ms.
 *   2. Finds the most recent ACTIVITY_RESUMED event.
 *   3. If that package is a known settings package → settings is open.
 *   4. Exposes a simple Listener interface for WatchdogService to react.
 *
 * Why UsageStatsManager over Accessibility:
 *   - Accessibility can be frozen/disabled (MIUI battery saver kills it).
 *   - UsageStatsManager is a privileged but stable system API.
 *   - It does NOT require the service to be running; WatchdogService polls it.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class SettingsAppMonitor {

    private static final String TAG = "SettingsAppMonitor";

    // ── Known settings packages across OEMs ──────────────────────────────────
    public static final Set<String> SETTINGS_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.settings",           // Stock Android
            "com.miui.securitycenter",        // MIUI — App Info lives here
            "com.google.android.settings",    // Pixel
            "com.samsung.android.settings",   // Samsung
            "com.huawei.systemmanager",       // EMUI/HarmonyOS
            "com.coloros.safecenter",         // ColorOS (Oppo/Realme)
            "com.vivo.permissionmanager",     // VivoUI
            "com.oneplus.security"            // OxygenOS
    ));

    // ── Listener interface ────────────────────────────────────────────────────

    public interface SettingsStateListener {
        /** Called on the polling thread when settings is newly opened. */
        void onSettingsOpened(String packageName);

        /** Called on the polling thread when settings is closed. */
        void onSettingsClosed();
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final Context context;
    private SettingsStateListener listener;

    private volatile boolean settingsCurrentlyOpen = false;
    private volatile String  currentSettingsPackage = null;

    private int notSettingsCount = 0;
    private static final int CLOSE_CONFIRM_THRESHOLD = 3; // 3 × 300ms = 900ms

    // ── Constructor ───────────────────────────────────────────────────────────

    public SettingsAppMonitor(Context context, SettingsStateListener listener) {
        this.context  = context.getApplicationContext();
        this.listener = listener;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Call this every ~300ms from WatchdogService's health-check thread.
     * Checks if a settings package is in the foreground and fires events.
     */
    public void poll() {
        String foreground = getForegroundPackage();
        boolean isSettingsNow = foreground != null && SETTINGS_PACKAGES.contains(foreground);

        if (isSettingsNow && !settingsCurrentlyOpen) {
            notSettingsCount = 0;
            settingsCurrentlyOpen = true;
            currentSettingsPackage = foreground;
            if (listener != null) listener.onSettingsOpened(foreground);
            Log.d("MONK_DEBUG", "Settings opened: " + foreground);

        } else if (!isSettingsNow && settingsCurrentlyOpen) {
            // null means no ACTIVITY_RESUMED found — ambiguous, treat cautiously
            if (foreground == null) {
                // Don't increment — this is a gap in events, not a confirmed switch
                Log.d("MONK_DEBUG", "Foreground null — holding settings open state");
                return;
            }
            notSettingsCount++;
            Log.d("MONK_DEBUG", "Not-settings count: " + notSettingsCount + " last pkg: " + foreground);

            if (notSettingsCount >= CLOSE_CONFIRM_THRESHOLD) {
                notSettingsCount = 0;
                settingsCurrentlyOpen = false;
                currentSettingsPackage = null;
                if (listener != null) listener.onSettingsClosed();
            }
        } else {
            Log.d("MONK_DEBUG", "Not-settings count: " + notSettingsCount);
            notSettingsCount = 0; // reset if settings is open again
        }
    }

    /** True if a settings app is currently in the foreground. */
    public boolean isSettingsOpen() {
        return settingsCurrentlyOpen;
    }

    /** Package name of the settings app that is open, or null. */
    public String getCurrentSettingsPackage() {
        return currentSettingsPackage;
    }

    // ── Core detection ────────────────────────────────────────────────────────

    /**
     * Returns the package name of the most recently resumed Activity.
     *
     * Strategy:
     *   - Query UsageEvents for the last 3 seconds.
     *   - Walk events in order, keep track of the latest ACTIVITY_RESUMED.
     *   - This is O(events in 3s) — typically only a handful.
     */
    private String getForegroundPackage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return null;

        UsageStatsManager usm =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return null;

        long now   = System.currentTimeMillis();
        long start = now - 3_000L; // look back 3 seconds

        try {
            UsageEvents events = usm.queryEvents(start, now);
            if (events == null) return null;

            UsageEvents.Event event      = new UsageEvents.Event();
            String            lastPkg    = null;
            long              lastTime   = 0L;

            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                Log.d("MONK_DEBUG", "Event: type=" + event.getEventType()
                        + " pkg=" + event.getPackageName()
                        + " time=" + event.getTimeStamp());

                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED
                        && event.getTimeStamp() >= lastTime) {
                    String pkg = event.getPackageName();
                    if (!pkg.equals(context.getPackageName())) {
                        lastPkg  = pkg;
                        lastTime = event.getTimeStamp();
                    }
                }
            }
            Log.d("MONK_DEBUG", "getForegroundPackage() returning: " + lastPkg);

            return lastPkg;

        } catch (Exception e) {
            Log.w(TAG, "UsageEvents query failed: " + e.getMessage());
            return null;
        }
    }
}