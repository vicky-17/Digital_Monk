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
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.digitalmonk.core.utils.Constants;
import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.vpn.DnsVpnService;
import com.example.digitalmonk.ui.dashboard.MainActivity;
import com.example.digitalmonk.core.utils.AlarmScheduler;

/**
 * Why we made this file:
 * On aggressive Android skins (MIUI, ColorOS), background apps are killed to save battery.
 * This service is the "heartbeat" of the app. It runs in the foreground with a
 * persistent notification to make it harder for the OS to kill.
 * * If the DnsVpnService dies, this Watchdog detects it within 30 seconds and
 * forcefully restarts it.
 */
public class WatchdogService extends Service {

    private static final String TAG = "WatchdogService";
    public static final int WATCHDOG_JOB_ID = 42;
    private static final long HEALTH_CHECK_INTERVAL_MS = 30_000L; // 30 seconds

    private HandlerThread healthCheckThread;
    private Handler handler;
    private PrefsManager prefs;

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
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler == null) return;

        // Check if already scheduled to avoid hitting Android rate limits
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (jobScheduler.getPendingJob(WATCHDOG_JOB_ID) != null) return;
        }

        JobInfo jobInfo = new JobInfo.Builder(
                WATCHDOG_JOB_ID,
                new ComponentName(context, WatchdogJobService.class))
                .setPeriodic(15 * 60 * 1000L) // 15 mins (Minimum)
                .setPersisted(true)            // Survive reboot
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .build();

        jobScheduler.schedule(jobInfo);
        Log.i(TAG, "Watchdog job scheduled as backup");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new PrefsManager(this);

        // Initialize the background worker thread
        healthCheckThread = new HandlerThread("watchdog-health-checker");
        healthCheckThread.start();
        handler = new Handler(healthCheckThread.getLooper());

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
        scheduleJobBackup(this);
        AlarmScheduler.scheduleRepeating(this);

        // START_STICKY: If the OS kills us, restart with a null intent ASAP.
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.w(TAG, "Task removed — scheduling alarm-based restart");

        Intent restartIntent = new Intent(getApplicationContext(), WatchdogService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                getApplicationContext(), 1, restartIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 3000L,
                    pendingIntent
            );
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (healthCheckThread != null) {
            healthCheckThread.quitSafely();
        }
        Log.w(TAG, "WatchdogService destroyed");
    }

    // ── Health Check Logic ────────────────────────────────────────────────────

    private final Runnable healthCheckRunnable = new Runnable() {
        @Override
        public void run() {
            performHealthCheck();
            handler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS);
        }
    };

    private void startHealthCheckLoop() {
        handler.removeCallbacks(healthCheckRunnable);
        handler.postDelayed(healthCheckRunnable, HEALTH_CHECK_INTERVAL_MS);
    }

    private void performHealthCheck() {
        Log.d(TAG, "🔍 Health check running...");

        // If VPN should be on but isn't, revive it
        if (prefs.isSafeSearchEnabled() && !DnsVpnService.isServiceRunning) {
            Log.w(TAG, "⚠️ DnsVpnService is dead — restarting");
            try {
                Intent intent = new Intent(this, DnsVpnService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to restart DnsVpnService", e);
            }
        }
    }

    private Notification buildNotification() {
        PendingIntent openIntent = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class),
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