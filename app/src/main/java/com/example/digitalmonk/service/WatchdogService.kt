package com.example.digitalmonk.service

import android.app.Notification
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.digitalmonk.core.utils.Constants
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.service.vpn.DnsVpnService
import com.example.digitalmonk.ui.dashboard.MainActivity

/**
 * WatchdogService — the immortal guardian.
 *
 * This is a persistent foreground service whose ONLY job is to stay alive
 * and restart other services if they die. On MIUI / aggressive OEMs,
 * background services get killed. The watchdog detects this and revives them.
 *
 * Restart strategies used (layered defense):
 *  1. START_STICKY         — Android restarts us automatically when killed
 *  2. Periodic health check — Handler loop every 30s checks if services are running
 *  3. JobScheduler backup  — Scheduled job re-launches us if somehow we die
 *  4. BootReceiver         — Re-launches us on every reboot
 *
 * On MIUI specifically:
 *  - We need the user to add us to "Autostart" whitelist (prompted in PermissionSetupScreen)
 *  - We need battery optimization disabled (prompted in PermissionSetupScreen)
 *  - We run as a foreground service with ongoing notification (visible = less likely to be killed)
 */
class WatchdogService : android.app.Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: PrefsManager

    companion object {
        private const val TAG = "WatchdogService"
        const val WATCHDOG_JOB_ID = 42
        private const val HEALTH_CHECK_INTERVAL_MS = 30_000L  // 30 seconds

        fun start(context: Context) {
            val intent = Intent(context, WatchdogService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start WatchdogService", e)
            }
        }

        fun scheduleJobBackup(context: Context) {
            // Schedule a periodic JobScheduler job as a backup restart mechanism.
            // Even if the service is killed and can't restart itself, the job will fire
            // and re-launch it.
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val jobInfo = JobInfo.Builder(
                WATCHDOG_JOB_ID,
                ComponentName(context, WatchdogJobService::class.java)
            )
                .setPeriodic(15 * 60 * 1000L)  // Every 15 minutes (minimum Android allows)
                .setPersisted(true)             // Survives reboot
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .build()

            jobScheduler.schedule(jobInfo)
            Log.i(TAG, "Watchdog job scheduled as backup")
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        Log.i(TAG, "WatchdogService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Watchdog started")

        // Must call startForeground immediately to avoid ANR on Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Constants.NOTIFICATION_ID_GUARDIAN,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID_GUARDIAN, buildNotification())
        }

        startHealthCheckLoop()
        scheduleJobBackup(this)

        // START_STICKY: Android will restart this service automatically if killed.
        // The intent may be null on restart, which is fine — we just restart the loop.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Called when the user swipes our app from recents.
        // Re-schedule ourselves to restart.
        Log.w(TAG, "Task removed — rescheduling restart")
        val restartIntent = Intent(applicationContext, WatchdogService::class.java)
        val pendingIntent = PendingIntent.getService(
            applicationContext, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        Log.w(TAG, "WatchdogService destroyed — will restart via START_STICKY or JobScheduler")
    }

    // ── Health check loop ─────────────────────────────────────────────────────

    private val healthCheckRunnable = object : Runnable {
        override fun run() {
            performHealthCheck()
            handler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS)
        }
    }

    private fun startHealthCheckLoop() {
        handler.removeCallbacks(healthCheckRunnable)
        handler.postDelayed(healthCheckRunnable, HEALTH_CHECK_INTERVAL_MS)
        Log.d(TAG, "Health check loop started (every ${HEALTH_CHECK_INTERVAL_MS / 1000}s)")
    }

    private fun performHealthCheck() {
        Log.d(TAG, "🔍 Health check running...")

        // If VPN filter was enabled and is no longer running, restart it
        if (prefs.safeSearchEnabled && !isDnsVpnRunning()) {
            Log.w(TAG, "⚠️ DnsVpnService is dead — restarting")
            try {
                val intent = Intent(this, DnsVpnService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart DnsVpnService", e)
            }
        }
    }

    private fun isDnsVpnRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == DnsVpnService::class.java.name }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.CHANNEL_GUARDIAN)
            .setContentTitle("Digital Monk is protecting this device")
            .setContentText("Parental controls are active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .setOngoing(true)   // Cannot be swiped away by user
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}