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

import com.example.digitalmonk.core.utils.AccessibilityHealthChecker;
import com.example.digitalmonk.core.utils.AlarmScheduler;
import com.example.digitalmonk.core.utils.Constants;
import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.monitor.SettingsAppMonitor;
import com.example.digitalmonk.service.monitor.SettingsPageReader;
import com.example.digitalmonk.service.overlay.GuardianOverlayService;
import com.example.digitalmonk.service.overlay.SettingsBlockOverlayService;
import com.example.digitalmonk.service.vpn.DnsVpnService;
import com.example.digitalmonk.ui.dashboard.MainActivity;

/**
 * WatchdogService — Updated for UsageStats-driven settings detection
 * ─────────────────────────────────────────────────────────────────────────────
 * TWO LOOPS now run on separate threads:
 *
 * 1. HEALTH CHECK LOOP (every 30s, existing)
 *    - VPN alive check
 *    - Accessibility frozen check → GuardianOverlayService
 *
 * 2. SETTINGS DETECTION LOOP (every 300ms, NEW)
 *    - SettingsAppMonitor.poll() → detects settings open/close via UsageStats
 *    - SettingsPageReader.readAndRespond() → reads page content if settings open
 *    - Drives SettingsBlockOverlayService state machine
 *
 * Why 300ms? Fast enough to show bottom overlay before user can tap Uninstall
 * (requires ~500ms of deliberate navigation). Cheap enough: UsageStats query
 * on 3s window typically processes <10 events.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class WatchdogService extends Service {

    private static final String TAG = "WatchdogService";

    public static final int WATCHDOG_JOB_ID = 42;

    // Health check interval (VPN, accessibility)
    private static final long HEALTH_CHECK_INTERVAL_MS = 30_000L;

    // Settings detection interval — fast polling
    private static final long SETTINGS_POLL_INTERVAL_MS = 300L;

    // ── Threads & Handlers ────────────────────────────────────────────────────
    private HandlerThread healthCheckThread;
    private Handler       healthHandler;

    private HandlerThread settingsPollThread;
    private Handler       settingsHandler;

    // ── Core dependencies ─────────────────────────────────────────────────────
    private PrefsManager       prefs;
    private SettingsAppMonitor settingsMonitor;
    private SettingsPageReader settingsPageReader;

    // ── Static Controller Methods ─────────────────────────────────────────────

    public static void start(Context context) {
        Intent intent = new Intent(context, WatchdogService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start WatchdogService", e);
        }
    }

    public static void scheduleJobBackup(Context context) {
        JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (js.getPendingJob(WATCHDOG_JOB_ID) != null) return;
        }

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

        // Health check thread (30s interval)
        healthCheckThread = new HandlerThread("watchdog-health");
        healthCheckThread.start();
        healthHandler = new Handler(healthCheckThread.getLooper());

        // Settings fast-poll thread (300ms interval)
        settingsPollThread = new HandlerThread("watchdog-settings-poll");
        settingsPollThread.start();
        settingsHandler = new Handler(settingsPollThread.getLooper());

        // SettingsAppMonitor with state listener
        settingsMonitor = new SettingsAppMonitor(this, new SettingsAppMonitor.SettingsStateListener() {
            @Override
            public void onSettingsOpened(String packageName) {
                // Initial entry into Settings shows the standard 100dp protection bar
                SettingsBlockOverlayService.showBottom(WatchdogService.this);
                if (settingsPageReader != null) settingsPageReader.reset();
            }

            @Override
            public void onSettingsClosed() {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {

                    if (!settingsMonitor.isSettingsOpen()) {
                        Log.d("MONK_DEBUG", "Watchdog: Confirmed settings closed. Triggering HIDE.");
                        if (!SettingsBlockOverlayService.isFullOverlay) {
                            SettingsBlockOverlayService.hide(WatchdogService.this);
                        }
                        if (settingsPageReader != null) {
                            settingsPageReader.reset();
                        }
                    } else {
                        Log.d("MONK_DEBUG", "Watchdog: Ignored false closed event - still in settings.");
                    }
                }, 500); // 500ms delay to account for page transition "flicker"
            }

        });

        settingsPageReader = new SettingsPageReader();

        Log.i(TAG, "WatchdogService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Watchdog started");

        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Constants.NOTIFICATION_ID_GUARDIAN, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(Constants.NOTIFICATION_ID_GUARDIAN, notification);
        }

        startHealthCheckLoop();
        startSettingsPollLoop();
        scheduleJobBackup(this);
        AlarmScheduler.scheduleRepeating(this);

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.w(TAG, "Task removed — scheduling restart");
        Intent restartIntent = new Intent(getApplicationContext(), WatchdogService.class);
        PendingIntent pi = PendingIntent.getService(
                getApplicationContext(), 1, restartIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.set(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 3000L, pi);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (healthHandler != null) healthHandler.removeCallbacksAndMessages(null);
        if (settingsHandler != null) settingsHandler.removeCallbacksAndMessages(null);
        if (healthCheckThread != null) healthCheckThread.quitSafely();
        if (settingsPollThread != null) settingsPollThread.quitSafely();
        Log.w(TAG, "WatchdogService destroyed");
    }

    // ── Health Check Loop (30s) ───────────────────────────────────────────────

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
        Log.d(TAG, "🔍 Health check…");

        // VPN watchdog
        if (prefs.isSafeSearchEnabled() && !DnsVpnService.isServiceRunning) {
            Log.w(TAG, "⚠️ DnsVpnService dead — restarting");
            try {
                Intent i = new Intent(this, DnsVpnService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
                else startService(i);
            } catch (Exception e) {
                Log.e(TAG, "VPN restart failed", e);
            }
        }

        // Accessibility health → GuardianOverlayService
        boolean needsLockdown    = AccessibilityHealthChecker.needsLockdown(this);
        boolean overlayShowing   = GuardianOverlayService.isRunning;

        if (needsLockdown && !overlayShowing) {
            boolean isFrozen = AccessibilityHealthChecker.isFrozen(this);
            GuardianOverlayService.start(this, isFrozen);
        } else if (!needsLockdown && overlayShowing) {
            GuardianOverlayService.stop(this);
        }
    }

    // ── Settings Detection Loop (300ms) ───────────────────────────────────────

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
        // Step 1: Detect if settings is open (UsageStats — always reliable)
        settingsMonitor.poll();

        // Step 2: If settings open, read the page content (accessibility — best effort)
        if (settingsMonitor.isSettingsOpen()) {
            String pkg = settingsMonitor.getCurrentSettingsPackage();
            settingsPageReader.readAndRespond(this, pkg);
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