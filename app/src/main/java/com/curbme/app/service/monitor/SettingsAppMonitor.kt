package com.curbme.app.service.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import kotlin.concurrent.Volatile

/**
 * SettingsAppMonitor
 * ─────────────────────────────────────────────────────────────────────────────
 * Detects when the user navigates INTO or OUT OF a settings app using
 * UsageStatsManager — NO accessibility required.
 * 
 * How it works:
 * 1. Queries UsageEvents for the last 3 seconds every 300ms.
 * 2. Finds the most recent ACTIVITY_RESUMED event.
 * 3. If that package is a known settings package → settings is open.
 * 4. Exposes a simple Listener interface for WatchdogService to react.
 * 
 * Why UsageStatsManager over Accessibility:
 * - Accessibility can be frozen/disabled (MIUI battery saver kills it).
 * - UsageStatsManager is a privileged but stable system API.
 * - It does NOT require the service to be running; WatchdogService polls it.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class SettingsAppMonitor(context: Context, private val listener: SettingsStateListener?) {
    // ── Listener interface ────────────────────────────────────────────────────
    interface SettingsStateListener {
        /** Called on the polling thread when settings is newly opened.  */
        fun onSettingsOpened(packageName: String?)

        /** Called on the polling thread when settings is closed.  */
        fun onSettingsClosed()
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private val context: Context

    /** True if a settings app is currently in the foreground.  */
    @Volatile
    var isSettingsOpen: Boolean = false
        private set

    /** Package name of the settings app that is open, or null.  */
    @Volatile
    var currentSettingsPackage: String? = null
        private set

    private var notSettingsCount = 0

    // ── Constructor ───────────────────────────────────────────────────────────
    init {
        this.context = context.getApplicationContext()
    }

    // ── Public API ────────────────────────────────────────────────────────────
    /**
     * Call this every ~300ms from WatchdogService's health-check thread.
     * Checks if a settings package is in the foreground and fires events.
     */
    fun poll() {
        val foreground = this.foregroundPackage
        val isSettingsNow = foreground != null && SETTINGS_PACKAGES.contains(foreground)

        //        Log.d("MONK_TRACE", "poll() → foreground=" + foreground + " | isSettingsNow=" + isSettingsNow + " | settingsCurrentlyOpen=" + settingsCurrentlyOpen);
        if (isSettingsNow && !this.isSettingsOpen) {
            notSettingsCount = 0
            this.isSettingsOpen = true
            currentSettingsPackage = foreground
            if (listener != null) listener.onSettingsOpened(foreground)

            //            Log.d("MONK_DEBUG", "Settings opened: " + foreground);
        } else if (!isSettingsNow && this.isSettingsOpen) {
            // null means no ACTIVITY_RESUMED found — ambiguous, treat cautiously
            if (foreground == null) {
                // Don't increment — this is a gap in events, not a confirmed switch
//                Log.d("MONK_DEBUG", "Foreground null — holding settings open state");
                return
            }
            notSettingsCount++

            //            Log.d("MONK_DEBUG", "Not-settings count: " + notSettingsCount + " last pkg: " + foreground);
            if (notSettingsCount >= CLOSE_CONFIRM_THRESHOLD) {
                notSettingsCount = 0
                this.isSettingsOpen = false
                currentSettingsPackage = null
                if (listener != null) listener.onSettingsClosed()
            }
        } else {
//            Log.d("MONK_DEBUG", "Not-settings count: " + notSettingsCount);
            notSettingsCount = 0 // reset if settings is open again
        }
    }

    // ── Core detection ────────────────────────────────────────────────────────
    private val foregroundPackage: String?
        /**
         * Returns the package name of the most recently resumed Activity.
         * 
         * Strategy:
         * - Query UsageEvents for the last 3 seconds.
         * - Walk events in order, keep track of the latest ACTIVITY_RESUMED.
         * - This is O(events in 3s) — typically only a handful.
         */
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return null

            val usm =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager?
            if (usm == null) return null

            val now = System.currentTimeMillis()
            val start = now - 3000L // look back 3 seconds

            try {
                val events = usm.queryEvents(start, now)
                if (events == null) return null

                val event = UsageEvents.Event()
                var lastPkg: String? = null
                var lastTime = 0L

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)

                    //                Log.d("MONK_DEBUG", "Event: type=" + event.getEventType()
//                        + " pkg=" + event.getPackageName()
//                        + " time=" + event.getTimeStamp());
                    if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED
                        && event.getTimeStamp() >= lastTime
                    ) {
                        val pkg = event.getPackageName()
                        if (pkg != context.getPackageName()) {
                            lastPkg = pkg
                            lastTime = event.getTimeStamp()
                        }
                    }
                }

                //            Log.d("MONK_DEBUG", "getForegroundPackage() returning: " + lastPkg);

//            Log.d("MONK_TRACE", "getForegroundPackage() → returning: " + lastPkg + " | lastTime=" + lastTime);
                return lastPkg
            } catch (e: Exception) {
//            Log.w(TAG, "UsageEvents query failed: " + e.getMessage());
                return null
            }
        }

    companion object {
        private const val TAG = "SettingsAppMonitor"

        // ── Known settings packages across OEMs ──────────────────────────────────
        @JvmField
        val SETTINGS_PACKAGES: MutableSet<String?> = HashSet<String?>(
            mutableListOf<String?>(
                "com.android.settings",  // Stock Android
                "com.miui.securitycenter",  // MIUI — App Info lives here
                "com.google.android.settings",  // Pixel
                "com.samsung.android.settings",  // Samsung
                "com.huawei.systemmanager",  // EMUI/HarmonyOS
                "com.coloros.safecenter",  // ColorOS (Oppo/Realme)
                "com.vivo.permissionmanager",  // VivoUI
                "com.oneplus.security" // OxygenOS
            )
        )

        private const val CLOSE_CONFIRM_THRESHOLD = 3 // 3 × 300ms = 900ms
    }
}