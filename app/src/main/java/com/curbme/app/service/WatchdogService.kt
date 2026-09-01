package com.curbme.app.service

import android.app.Notification
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.curbme.app.core.deviceowner.DevicePolicyHelper
import com.curbme.app.core.utils.AlarmScheduler
import com.curbme.app.core.utils.Constants
import com.curbme.app.core.utils.NtpFetcher
import com.curbme.app.data.local.prefs.DataStoreManager
import com.curbme.app.data.local.prefs.Settings as MonkSettings
import com.curbme.app.service.accessibility.AllowlistManager
import com.curbme.app.service.monitor.*
import com.curbme.app.service.overlay.SettingsBlockOverlayService
import com.curbme.app.service.vpn.DnsVpnService
import com.curbme.app.ui.block.BlockedPageActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * WatchdogService — professional Kotlin version with Coroutines and DataStore.
 */
class WatchdogService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var dnsObserver: ContentObserver? = null

    private lateinit var dataStoreManager: DataStoreManager
    private var settings = MonkSettings()

    private var settingsMonitor: SettingsAppMonitor? = null
    private var settingsPageReader: SettingsPageReader? = null
    private var protectionMonitor: ProtectionStateMonitor? = null

    private var lastKnownIssues: Set<ProtectionIssue>? = null
    private var lastBlockScreenShownMs = 0L

    private val healthHandler = Handler(Looper.getMainLooper())
    private val settingsHandler = Handler(Looper.getMainLooper())
    private val protectionHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        dataStoreManager = DataStoreManager(this)

        serviceScope.launch {
            dataStoreManager.settings.collectLatest {
                settings = it
            }
        }

        dnsObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                DevicePolicyHelper.reapplyPolicyIfMismatched(this@WatchdogService)
                protectionHandler.postDelayed({
                    DevicePolicyHelper.reapplyPolicyIfMismatched(this@WatchdogService)
                }, 300L)
            }
        }
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor("private_dns_mode"),
            false,
            dnsObserver!!
        )
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor("private_dns_specifier"),
            false,
            dnsObserver!!
        )

        settingsMonitor = SettingsAppMonitor(this, object : SettingsAppMonitor.SettingsStateListener {
            override fun onSettingsOpened(packageName: String?) {
                SettingsBlockOverlayService.showBottom(this@WatchdogService)
                settingsPageReader?.reset()
                settingsHandler.postDelayed({
                    if (settingsMonitor?.isSettingsOpen == true) {
                        settingsPageReader?.readAndRespond(this@WatchdogService, packageName ?: "")
                    }
                }, 1000L)
            }

            override fun onSettingsClosed() {
                settingsHandler.postDelayed({
                    if (settingsMonitor?.isSettingsOpen == false) {
                        if (!SettingsBlockOverlayService.isFullOverlay) {
                            SettingsBlockOverlayService.hide(this@WatchdogService)
                        }
                        settingsPageReader?.reset()
                    }
                }, 500)
            }
        })

        // SettingsPageReader might need update to use MonkSettings or I'll just pass prefs for now if it still uses it
        // Actually, I'll update SettingsPageReader too later if needed.
        settingsPageReader = SettingsPageReader(com.curbme.app.data.local.prefs.PrefsManager(this))
        protectionMonitor = ProtectionStateMonitor(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Constants.NOTIFICATION_ID_GUARDIAN, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Constants.NOTIFICATION_ID_GUARDIAN, notification)
        }

        startHealthCheckLoop()
        startSettingsPollLoop()
        startProtectionCheckLoop()
        startAppBlockEngine(this)
        scheduleJobBackup(this)
        AlarmScheduler.scheduleRepeating(this)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        dnsObserver?.let { contentResolver.unregisterContentObserver(it) }
        serviceJob.cancel()
        healthHandler.removeCallbacksAndMessages(null)
        settingsHandler.removeCallbacksAndMessages(null)
        protectionHandler.removeCallbacksAndMessages(null)
    }

    private fun startHealthCheckLoop() {
        healthHandler.postDelayed(object : Runnable {
            override fun run() {
                performHealthCheck()
                healthHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS)
            }
        }, HEALTH_CHECK_INTERVAL_MS)
    }

    private fun performHealthCheck() {
        AllowlistManager.getInstance().pruneExpired()
        startAppBlockEngine(this)

        if (settings.isSafeSearchEnabled && !DnsVpnService.isServiceRunning) {
            try {
                val i = Intent(this, DnsVpnService::class.java)
                startForegroundService(i)
            } catch (e: Exception) {
                Log.e(TAG, "VPN restart failed", e)
            }
        }

        if (settings.lockDurationMs > 0) {
            val lastKnown = settings.lastKnownDeviceTime
            val now = System.currentTimeMillis()
            if (now - lastKnown > 20 * 60 * 1000L) {
                serviceScope.launch(Dispatchers.IO) {
                    val ntpTime = NtpFetcher.fetchNtpTime()
                    if (ntpTime > 0) {
                        dataStoreManager.updateSettings { 
                            it.copy(lockNtpOffset = ntpTime - now, lastKnownDeviceTime = now)
                        }
                    }
                }
            }
        }
    }

    private fun startSettingsPollLoop() {
        settingsHandler.post(object : Runnable {
            override fun run() {
                performSettingsPoll()
                settingsHandler.postDelayed(this, SETTINGS_POLL_INTERVAL_MS)
            }
        })
    }

    private fun performSettingsPoll() {
        settingsMonitor?.poll()
        if (settingsMonitor?.isSettingsOpen == true) {
            DevicePolicyHelper.reapplyPolicyIfMismatched(this)
            val pkg = settingsMonitor?.currentSettingsPackage ?: return
            val isDangerous = settingsPageReader?.readAndRespond(this, pkg) ?: false
            if (!isDangerous && !SettingsBlockOverlayService.isFullOverlay && SettingsBlockOverlayService.isRunning) {
                SettingsBlockOverlayService.shrinkToBottom(this)
            }
        }
    }

    private fun startProtectionCheckLoop() {
        protectionHandler.postDelayed(object : Runnable {
            override fun run() {
                performProtectionCheck()
                protectionHandler.postDelayed(this, PROTECTION_CHECK_INTERVAL_MS)
            }
        }, 5000L)
    }

    private fun performProtectionCheck() {
        DevicePolicyHelper.reapplyPolicyIfMismatched(this)
        val currentApp = lastForegroundPackage // Shared with AccessibilityService
        val currentIssuesRaw = protectionMonitor?.check(currentApp) ?: emptySet()
        val currentIssues = currentIssuesRaw.filterNotNull().toSet()

        val changed = currentIssues != lastKnownIssues
        if (changed) {
            lastKnownIssues = currentIssues
            Log.d(TAG, "Protection state changed: $currentIssues")
            protectionStateListener?.onIssuesChanged(currentIssues)
        }

        if (currentIssues.isEmpty()) return

        val now = System.currentTimeMillis()
        val shouldShow = changed || (now - lastBlockScreenShownMs >= BLOCK_SCREEN_RESHOW_INTERVAL_MS)

        if (shouldShow) {
            lastBlockScreenShownMs = now
            handleProtectionIssues(currentIssues)
        }
    }

    private fun handleProtectionIssues(issues: Set<ProtectionIssue>) {
        val topIssue = issues.minByOrNull { it.priority } ?: return

        when (topIssue) {
            ProtectionIssue.ANOTHER_VPN_ACTIVE -> startActivity(BlockedPageActivity.anotherVpnActive(this))
            ProtectionIssue.VPN_PERMISSION_REVOKED -> startActivity(BlockedPageActivity.vpnPermissionRevoked(this))
            ProtectionIssue.ACCESSIBILITY_DISABLED -> {
                if (settings.isBankingBypassEnabled) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(this, "Blocked during Banking Mode. Only ${settings.bankingBypassPackage ?: "allowed app"} is allowed.", Toast.LENGTH_LONG).show()
                    }
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                } else {
                    startActivity(BlockedPageActivity.accessibilityDisabled(this))
                }
            }
            ProtectionIssue.OVERLAY_PERMISSION_MISSING -> startActivity(BlockedPageActivity.overlayPermissionMissing(this))
            ProtectionIssue.USAGE_STATS_MISSING -> startActivity(BlockedPageActivity.usageStatsMissing(this))
            ProtectionIssue.BATTERY_OPTIMIZATION_ACTIVE -> startActivity(BlockedPageActivity.batteryOptimizationActive(this))
            ProtectionIssue.ALWAYS_ON_VPN_NOT_SET -> startActivity(BlockedPageActivity.alwaysOnVpnNotSet(this))
            else -> Log.w(TAG, "Unhandled issue: $topIssue")
        }
    }

    private fun buildNotification(): Notification {
        return com.curbme.app.service.notification.NotificationHelper.buildGuardianForegroundNotification(this)
    }

    companion object {
        private const val TAG = "WatchdogService"
        private const val HEALTH_CHECK_INTERVAL_MS = 30_000L
        private const val SETTINGS_POLL_INTERVAL_MS = 300L
        private const val PROTECTION_CHECK_INTERVAL_MS = 5_000L
        private const val BLOCK_SCREEN_RESHOW_INTERVAL_MS = 3_000L

        @Volatile
        var lastForegroundPackage: String? = null

        var protectionStateListener: ProtectionStateListener? = null

        fun start(context: Context) {
            val intent = Intent(context, WatchdogService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {}
        }

        fun startAppBlockEngine(context: Context) {
            val intent = Intent(context, AppBlockEngineService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {}
        }

        fun scheduleJobBackup(context: Context) {
            val js = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (js.getPendingJob(42) != null) return

            val job = JobInfo.Builder(42, ComponentName(context, WatchdogJobService::class.java))
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build()
            js.schedule(job)
        }
    }

    interface ProtectionStateListener {
        fun onIssuesChanged(issues: Set<ProtectionIssue>)
    }
}
