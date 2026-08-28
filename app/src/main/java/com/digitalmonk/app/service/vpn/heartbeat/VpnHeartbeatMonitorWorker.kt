package com.digitalmonk.app.service.vpn.heartbeat

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager.Companion.getInstance
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.digitalmonk.app.data.local.prefs.PrefsManager
import com.digitalmonk.app.service.vpn.DnsVpnService
import java.util.concurrent.TimeUnit

/**
 * Why we made this file:
 * Android memory management is aggressive. Even Foreground Services can be killed
 * by heavily customized OEM skins (like Xiaomi's MIUI or Samsung's OneUI).
 * 
 * This WorkManager class acts as the "Watchdog" for the VPN. Because WorkManager
 * is deeply integrated into the Android OS, it survives app swiping and reboots.
 * It wakes up every 15 minutes, checks the "Heartbeat" defined in PrefsManager, and
 * forcefully revives the VPN if it detects the system killed it.
 * 
 * What the file name defines:
 * "HeartbeatMonitor" identifies its role in checking the life status of another component.
 * "Worker" identifies it as a WorkManager task.
 */
class VpnHeartbeatMonitorWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {
    // ── Background Execution ──────────────────────────────────────────────────
    override fun doWork(): Result {
        Log.d(TAG, "Heartbeat check running…")

        val context = getApplicationContext()
        val prefs = PrefsManager(context)

        // 1. Initial Logic Checks
        // If the user has turned off the VPN filter, don't restart
        if (!prefs.isSafeSearchEnabled) {
            Log.d(TAG, "VPN filter is off — no restart needed")
            return Result.success()
        }

        // If keep-alive is disabled by parent, skip
        if (!prefs.isKeepVpnAlive) {
            Log.d(TAG, "Keep VPN alive is off — skipping")
            return Result.success()
        }

        // Check if service is already running using the static flag
        if (DnsVpnService.isServiceRunning) {
            Log.d(TAG, "✅ DnsVpnService is running — no action needed")
            return Result.success()
        }

        // 2. State Verification
        // Service is NOT running — check if it was killed unexpectedly
        val lastHeartbeatType = prefs.lastVpnHeartbeatType
        Log.d(TAG, "Last heartbeat: " + lastHeartbeatType + " | service running: false")

        // If last recorded state was 'ALIVE' but service isn't running, it was killed
        if (VpnHeartBeatEntity.TYPE_ALIVE == lastHeartbeatType) {
            Log.w(TAG, "⚠️ VPN was killed unexpectedly — restarting")
            restartVpn(context)
        } else {
            Log.d(TAG, "VPN was stopped cleanly — no restart")
        }

        return Result.success()
    }

    /**
     * Re-launches the DnsVpnService as a Foreground Service.
     */
    private fun restartVpn(context: Context) {
        try {
            val intent = Intent(context, DnsVpnService::class.java)
            context.startForegroundService(intent)
            Log.i(TAG, "✅ DnsVpnService restarted by heartbeat watchdog")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart DnsVpnService from watchdog", e)
        }
    }

    companion object {
        private const val TAG = "VpnHeartbeatMonitor"
        const val WORK_NAME: String = "vpn_heartbeat_monitor"

        // ── Static Helper Methods ─────────────────────────────────────────────────
        /**
         * Schedules (or re-schedules) the periodic watchdog.
         * Call this from DnsVpnService.startVpn() and from BootReceiver.
         */
        @JvmStatic
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequest.Builder(
                VpnHeartbeatMonitorWorker::class.java, 15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // Don't replace if already running
                request
            )
            Log.i(TAG, "Heartbeat monitor scheduled")
        }

        /** * Cancel the watchdog — called when VPN is cleanly stopped by the user.
         */
        @JvmStatic
        fun cancel(context: Context) {
            getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Heartbeat monitor cancelled")
        }
    }
}