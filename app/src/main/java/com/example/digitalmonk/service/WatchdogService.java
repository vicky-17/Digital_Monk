package com.example.digitalmonk.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.digitalmonk.core.utils.AlarmScheduler;
import com.example.digitalmonk.core.utils.Constants;
import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.accessibility.AllowlistManager;
import com.example.digitalmonk.service.monitor.ProtectionIssue;
import com.example.digitalmonk.service.monitor.ProtectionStateMonitor;
import com.example.digitalmonk.service.monitor.SettingsAppMonitor;
import com.example.digitalmonk.service.monitor.SettingsPageReader;
import com.example.digitalmonk.service.overlay.SettingsBlockOverlayService;
import com.example.digitalmonk.service.vpn.DnsVpnService;
import com.example.digitalmonk.ui.MainActivity;
import com.example.digitalmonk.core.utils.NtpFetcher;
import com.example.digitalmonk.ui.block.BlockedPageActivity;

import java.util.Set;

/**
 * WatchdogService — Three parallel loops:
 *
 * 1. HEALTH CHECK LOOP (every 30s)
 *    - VPN alive check
 *    - NTP offset refresh
 *
 * 2. SETTINGS DETECTION LOOP (every 300ms)
 *    - SettingsAppMonitor.poll() → detects settings open/close via UsageStats
 *    - SettingsPageReader.readAndRespond() → reads page content if settings open
 *    - Drives SettingsBlockOverlayService state machine
 *
 * 3. PROTECTION STATE LOOP (every 10s)  ← NEW
 *    - ProtectionStateMonitor.check() → detects permission gaps and VPN issues
 *    - Notifies ProtectionStateListener with the current issue set
 *    - Only fires the listener when the issue set CHANGES (avoids spam)
 */
public class WatchdogService extends Service {

    private static final String TAG = "WatchdogService";

    public static final int WATCHDOG_JOB_ID = 42;

    // ── Loop intervals ────────────────────────────────────────────────────────
    private static final long HEALTH_CHECK_INTERVAL_MS    = 30_000L;
    private static final long SETTINGS_POLL_INTERVAL_MS   = 300L;
    /** Check permissions and VPN state every 10 seconds. */
    private static final long PROTECTION_CHECK_INTERVAL_MS = 1_000L;

    // ── Threads & Handlers ────────────────────────────────────────────────────
    private HandlerThread healthCheckThread;
    private Handler       healthHandler;

    private HandlerThread settingsPollThread;
    private Handler       settingsHandler;

    private HandlerThread protectionCheckThread;   // NEW
    private Handler       protectionHandler;       // NEW

    // ── Core dependencies ─────────────────────────────────────────────────────
    private PrefsManager           prefs;
    private SettingsAppMonitor     settingsMonitor;
    private SettingsPageReader     settingsPageReader;
    private ProtectionStateMonitor protectionMonitor;  // NEW

    // ── Protection state tracking (change detection) ──────────────────────────
    /**
     * Last known issue set. We only fire the listener when this changes,
     * so callers aren't flooded with identical callbacks every 10 seconds.
     */
    private Set<ProtectionIssue> lastKnownIssues = null;

    // ✅ NEW: tracks when the block screen was last shown
    // Allows re-showing after a cooldown even if issues haven't changed
    private long lastBlockScreenShownMs = 0L;
    private static final long BLOCK_SCREEN_RESHOW_INTERVAL_MS = 3_000L; // reshow every 3s

    // ── Listener interface ────────────────────────────────────────────────────

    /**
     * Implement this interface to react to protection state changes.
     *
     * onIssuesChanged() is called on the protection-check background thread —
     * post to the main thread if you need to update UI.
     *
     * Wire up via setProtectionStateListener() after obtaining the service
     * instance, or — more commonly — have WatchdogService drive a static
     * method or broadcast directly (see onIssuesChanged impl below).
     */
    public interface ProtectionStateListener {
        /**
         * @param issues  Current set of active issues. Empty = everything healthy.
         */
        void onIssuesChanged(Set<ProtectionIssue> issues);
    }

    private static volatile ProtectionStateListener protectionStateListener = null;

    /** Wire up a listener from outside (e.g. from a ViewModel or Activity). */
    public static void setProtectionStateListener(ProtectionStateListener listener) {
        protectionStateListener = listener;
    }

    /** Remove a previously registered listener. */
    public static void clearProtectionStateListener() {
        protectionStateListener = null;
    }

    // ── Static Controller Methods ─────────────────────────────────────────────

    public static void start(Context context) {
        Intent intent = new Intent(context, WatchdogService.class);
        try {
            context.startForegroundService(intent);
            context.startService(intent);
        } catch (Exception e) {
//            Log.e(TAG, "Failed to start WatchdogService", e);
        }
    }

    public static void scheduleJobBackup(Context context) {
        JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;

        if (js.getPendingJob(WATCHDOG_JOB_ID) != null) return;

        JobInfo job = new JobInfo.Builder(
                WATCHDOG_JOB_ID,
                new ComponentName(context, WatchdogJobService.class))
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .build();

        js.schedule(job);
        Log.i(TAG, "Watchdog job scheduled");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new PrefsManager(this);

        // Loop 1: health check (30s)
        healthCheckThread = new HandlerThread("watchdog-health");
        healthCheckThread.start();
        healthHandler = new Handler(healthCheckThread.getLooper());

        // Loop 2: settings fast-poll (300ms)
        settingsPollThread = new HandlerThread("watchdog-settings-poll");
        settingsPollThread.start();
        settingsHandler = new Handler(settingsPollThread.getLooper());

        // Loop 3: protection state check (10s) ← NEW
        protectionCheckThread = new HandlerThread("watchdog-protection-check");
        protectionCheckThread.start();
        protectionHandler = new Handler(protectionCheckThread.getLooper());

        // SettingsAppMonitor with state listener
        settingsMonitor = new SettingsAppMonitor(this, new SettingsAppMonitor.SettingsStateListener() {
            @Override
            public void onSettingsOpened(String packageName) {
                SettingsBlockOverlayService.showBottom(WatchdogService.this);
                if (settingsPageReader != null) settingsPageReader.reset();
                settingsHandler.postDelayed(() -> {
                    if (settingsMonitor.isSettingsOpen()) {
                        settingsPageReader.readAndRespond(WatchdogService.this, packageName);
                    }
                }, 1000L);
            }

            @Override
            public void onSettingsClosed() {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!settingsMonitor.isSettingsOpen()) {
//                        Log.d("MONK_DEBUG", "Watchdog: Confirmed settings closed. Triggering HIDE.");
                        if (!SettingsBlockOverlayService.isFullOverlay) {
                            SettingsBlockOverlayService.hide(WatchdogService.this);
                        }
                        if (settingsPageReader != null) {
                            settingsPageReader.reset();
                        }
                    } else {
//                        Log.d("MONK_DEBUG", "Watchdog: Ignored false closed event - still in settings.");
                    }
                }, 500); // 500ms delay to account for page transition "flicker"
            }
        });

        settingsPageReader  = new SettingsPageReader();
        protectionMonitor   = new ProtectionStateMonitor(this);  // NEW
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Constants.NOTIFICATION_ID_GUARDIAN, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(Constants.NOTIFICATION_ID_GUARDIAN, notification);
        }

        startHealthCheckLoop();
        startSettingsPollLoop();
        startProtectionCheckLoop();  // NEW
        scheduleJobBackup(this);
        AlarmScheduler.scheduleRepeating(this);

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restartIntent = new Intent(getApplicationContext(), WatchdogService.class);
        PendingIntent pi = PendingIntent.getService(
                getApplicationContext(), 1, restartIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 3000L, pi);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (healthHandler    != null) healthHandler.removeCallbacksAndMessages(null);
        if (settingsHandler  != null) settingsHandler.removeCallbacksAndMessages(null);
        if (protectionHandler != null) protectionHandler.removeCallbacksAndMessages(null);  // NEW
        if (healthCheckThread   != null) healthCheckThread.quitSafely();
        if (settingsPollThread  != null) settingsPollThread.quitSafely();
        if (protectionCheckThread != null) protectionCheckThread.quitSafely();  // NEW
    }

    // ── Loop 1: Health Check (30s) ────────────────────────────────────────────

    private final Runnable healthCheckRunnable = new Runnable() {
        @Override
        public void run() {
            performHealthCheck();
            healthHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS);
        }
    };

    private void startHealthCheckLoop() {
        healthHandler.removeCallbacks(healthCheckRunnable);
        healthHandler.postDelayed(healthCheckRunnable, HEALTH_CHECK_INTERVAL_MS);
    }

    private void performHealthCheck() {
//        Log.d(TAG, "🔍 Health check…");
        AllowlistManager.getInstance().pruneExpired(); // Clean expired allowlist entries

        // VPN watchdog
        if (prefs.isSafeSearchEnabled() && !DnsVpnService.isServiceRunning) {
//            Log.w(TAG, "⚠️ DnsVpnService dead — restarting");
            try {
                Intent i = new Intent(this, DnsVpnService.class);
                startForegroundService(i);
            } catch (Exception e) {
//                Log.e(TAG, "VPN restart failed", e);
            }
        }

        // Refresh NTP offset periodically while lock is active
        if (prefs.getLockDurationMs() > 0) {
            long lastKnown = prefs.getLastKnownDeviceTime();
            long now = System.currentTimeMillis();
            if (now - lastKnown > 20 * 60 * 1000L) {
                long ntpTime = NtpFetcher.fetchNtpTime();
                if (ntpTime > 0) {
                    prefs.setLockNtpOffset(ntpTime - now);
                }
                prefs.setLastKnownDeviceTime(now);
            }
        }
    }

    // ── Loop 2: Settings Detection (300ms) ────────────────────────────────────

    private final Runnable settingsPollRunnable = new Runnable() {
        @Override
        public void run() {
            performSettingsPoll();
            settingsHandler.postDelayed(this, SETTINGS_POLL_INTERVAL_MS);
        }
    };

    private void startSettingsPollLoop() {
        settingsHandler.removeCallbacks(settingsPollRunnable);
        // Start immediately (no initial delay — want instant detection)
        settingsHandler.post(settingsPollRunnable);
    }

    private void performSettingsPoll() {
        settingsMonitor.poll();

        if (settingsMonitor.isSettingsOpen()) {
            String pkg = settingsMonitor.getCurrentSettingsPackage();
//            Log.d("MONK_TRACE", "performSettingsPoll() → settings IS open, pkg=" + pkg + " → calling readAndRespond");
            boolean isDangerous = settingsPageReader.readAndRespond(this, pkg);
//            Log.d("MONK_TRACE", "performSettingsPoll() → readAndRespond result: isDangerous=" + isDangerous);

            if (!isDangerous && !SettingsBlockOverlayService.isFullOverlay
                    && SettingsBlockOverlayService.isRunning) {
                SettingsBlockOverlayService.shrinkToBottom(this);
            }
        }
    }

    // ── Loop 3: Protection State Check (10s) ─────────────────────────────────

    private final Runnable protectionCheckRunnable = new Runnable() {
        @Override
        public void run() {
            performProtectionCheck();
            protectionHandler.postDelayed(this, PROTECTION_CHECK_INTERVAL_MS);
        }
    };

    private void startProtectionCheckLoop() {
        protectionHandler.removeCallbacks(protectionCheckRunnable);
        // First check fires after a short grace period so the service is
        // fully started before we evaluate anything (avoids false positives
        // on boot when services are still initialising).
        protectionHandler.postDelayed(protectionCheckRunnable, 5_000L);
    }

    private void performProtectionCheck() {
        Set<ProtectionIssue> currentIssues = protectionMonitor.check();

        boolean changed = !currentIssues.equals(lastKnownIssues);

        // Always update and notify on change
        if (changed) {
            lastKnownIssues = currentIssues;

            Log.d(TAG, "Protection state changed → issues: "
                    + (currentIssues.isEmpty() ? "none" : currentIssues.toString()));

            ProtectionStateListener listener = protectionStateListener;
            if (listener != null) {
                listener.onIssuesChanged(currentIssues);
            }
        }

        if (currentIssues.isEmpty()) return;

        // ✅ Show block screen if:
        //    (a) issues just appeared (changed from empty/null), OR
        //    (b) issues are still present after cooldown (user dismissed without fixing)
        long now = System.currentTimeMillis();
        boolean shouldShow = changed
                || (now - lastBlockScreenShownMs >= BLOCK_SCREEN_RESHOW_INTERVAL_MS);

        if (shouldShow) {
            lastBlockScreenShownMs = now;
            handleProtectionIssues(currentIssues);
        }
    }

    /**
     * Built-in reactions to protection issues.
     *
     * Currently: just logs them. Block screen wiring will be added in the
     * next step once the UI component is ready.
     *
     * Convention: react to the highest-priority issue first; lower-priority
     * issues are still in the set and available for the listener to handle.
     */
    private void handleProtectionIssues(Set<ProtectionIssue> issues) {
        if (issues.isEmpty()) return;

        ProtectionIssue topIssue = null;
        for (ProtectionIssue issue : issues) {
            if (topIssue == null || issue.priority < topIssue.priority) {
                topIssue = issue;
            }
        }
        if (topIssue == null) return;

        switch (topIssue) {

            case VPN_SERVICE_DEAD:
                // Health loop handles restart — no block screen needed
                Log.w(TAG, "Protection: VPN service dead (health loop will revive)");
                break;

            case ANOTHER_VPN_ACTIVE:
                Log.w(TAG, "Protection: foreign VPN active — showing block screen");
                startActivity(BlockedPageActivity.Companion.anotherVpnActive(this));
                break;

            case VPN_PERMISSION_REVOKED:
                Log.w(TAG, "Protection: VPN permission revoked — showing block screen");
                startActivity(BlockedPageActivity.Companion.vpnPermissionRevoked(this));
                break;

            case ACCESSIBILITY_DISABLED:
                Log.w(TAG, "Protection: Accessibility off — showing block screen");
                startActivity(BlockedPageActivity.Companion.accessibilityDisabled(this));
                break;

            case OVERLAY_PERMISSION_MISSING:
                Log.w(TAG, "Protection: Overlay permission missing — showing block screen");
                startActivity(BlockedPageActivity.Companion.overlayPermissionMissing(this));
                break;

            case USAGE_STATS_MISSING:
                Log.w(TAG, "Protection: Usage stats missing — showing block screen");
                startActivity(BlockedPageActivity.Companion.usageStatsMissing(this));
                break;

            case BATTERY_OPTIMIZATION_ACTIVE:
                Log.w(TAG, "Protection: Battery optimization active — showing block screen");
                startActivity(BlockedPageActivity.Companion.batteryOptimizationActive(this));
                break;

            case ALWAYS_ON_VPN_NOT_SET:
                // Informational only — not severe enough to block
                Log.i(TAG, "Protection: Always-On VPN not set to Digital Monk");
                startActivity(BlockedPageActivity.Companion.alwaysOnVpnNotSet(this));
                break;

            default:
                Log.w(TAG, "Protection: unhandled issue " + topIssue);
                break;
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private Notification buildNotification() {
        PendingIntent openIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, Constants.CHANNEL_GUARDIAN)
                .setContentTitle("Digital Monk Protection Active")
                .setContentText("Parental controls are currently active")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}