package com.digitalmonk.app.service.monitor

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.digitalmonk.app.data.local.db.AppDatabase
import com.digitalmonk.app.data.local.db.entity.AppBlockRule
import kotlinx.coroutines.*

class AppBlockEngineService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var db: AppDatabase

    private var lastForegroundApp: String? = null

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        db = AppDatabase.getDatabase(this)
        startForegroundService()
        startMonitoring()
    }

    private fun startForegroundService() {
        val channelId = "app_blocker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "App Protection", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Digital Monk Protection Active")
            .setContentText("Monitoring app usage...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .build()

        startForeground(1001, notification)
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                checkForegroundApp()
                delay(1000) // Check every second
            }
        }
    }

    private suspend fun checkForegroundApp() {
        val currentApp = getForegroundPackage() ?: return

        // Don't block our own app or if the app hasn't changed
        if (currentApp == packageName || currentApp == lastForegroundApp) return

        lastForegroundApp = currentApp

        // Check if there is a rule for this app
        val rule = db.appBlockDao().getRuleForPackage(currentApp) ?: return

        if (shouldBlock(rule)) {
            applyBlock(rule)
        }
    }

    private fun getForegroundPackage(): String? {
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 2000, time)
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastPackage = event.packageName
            }
        }
        return lastPackage
    }

    private fun shouldBlock(rule: AppBlockRule): Boolean {
        return when (rule.planType) {
            "STAY_FOCUSED" -> true
            "TIME_LIMIT" -> true
            "HABIT_TRAINING" -> true
            else -> false
        }
    }

    private fun applyBlock(rule: AppBlockRule) {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val adminComponent = android.content.ComponentName(this, com.digitalmonk.app.receiver.MonkDeviceAdminReceiver::class.java)
        val isDO = dpm.isDeviceOwnerApp(packageName)

        when (rule.blockMethod) {
            "INTERSTITIAL" -> {
                val intent = com.digitalmonk.app.ui.locks.InterstitialActivity.createIntent(
                    this, rule.packageName, rule.appName, "${rule.allowedMinutes}m limit"
                )
                startActivity(intent)
            }
            "SUSPEND" -> {
                if (isDO) {
                    try {
                        dpm.setPackagesSuspended(adminComponent, arrayOf(rule.packageName), true)
                    } catch (e: Exception) {
                        returnToHome()
                    }
                } else {
                    returnToHome()
                }
            }
            "HIDE" -> {
                if (isDO) {
                    try {
                        dpm.setApplicationHidden(adminComponent, rule.packageName, true)
                    } catch (e: Exception) {
                        returnToHome()
                    }
                } else {
                    returnToHome()
                }
            }
            "KILL" -> {
                // Kill requires a bit more effort on Android without accessibility,
                // but DO can use forceStopPackage (hidden API in some versions but usually available to DO)
                // We'll use returnToHome + a background kill hint
                returnToHome()
            }
            else -> returnToHome()
        }

        // Anti-uninstall protection (if enabled)
        if (rule.isAntiUninstallEnabled && isDO) {
            try {
                dpm.setUninstallBlocked(adminComponent, rule.packageName, true)
            } catch (e: Exception) {
                android.util.Log.e("AppBlockEngine", "Failed to set anti-uninstall", e)
            }
        }
    }

    private fun returnToHome() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}