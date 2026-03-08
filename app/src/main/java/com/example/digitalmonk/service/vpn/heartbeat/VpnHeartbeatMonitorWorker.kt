package com.example.digitalmonk.service.vpn.heartbeat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.service.vpn.DnsVpnService
import java.util.concurrent.TimeUnit

/**
 * WorkManager periodic worker — Layer 1 of the VPN keep-alive system.
 *
 * Fires every 15 minutes (Android's minimum periodic interval).
 * Logic (borrowed from DDG's VpnServiceHeartbeatMonitor.kt):
 *   1. Read the last heartbeat from Room DB
 *   2. If last heartbeat == ALIVE and DnsVpnService is NOT running
 *      → the VPN was killed unexpectedly → restart it
 *   3. If last heartbeat == STOPPED → user turned it off cleanly → do nothing
 *
 * Why WorkManager instead of just JobScheduler?
 *   WorkManager survives app updates and device restarts (setPersisted equivalent),
 *   and is harder for OEM memory managers to kill than a plain service.
 *
 * Reference: DDG VpnServiceHeartbeatMonitor.kt
 *   app-tracking-protection/vpn-impl/.../heartbeat/VpnServiceHeartbeatMonitor.kt
 */
class VpnHeartbeatMonitorWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "VpnHeartbeatMonitor"
        const val WORK_NAME = "vpn_heartbeat_monitor"

        /**
         * Schedules (or re-schedules) the periodic watchdog.
         * Call this from DnsVpnService.startVpn() and from BootReceiver.
         *
         * KEEP_EXISTING: if the job is already queued, don't reschedule —
         * prevents duplicate workers stacking up.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<VpnHeartbeatMonitorWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // Don't replace if already running
                request
            )
            Log.i(TAG, "Heartbeat monitor scheduled")
        }

        /** Cancel the watchdog — called when VPN is cleanly stopped by the user. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Heartbeat monitor cancelled")
        }
    }

    override fun doWork(): Result {
        Log.d(TAG, "Heartbeat check running…")

        val prefs = PrefsManager(context)

        // If the user has turned off the VPN filter, don't restart
        if (!prefs.safeSearchEnabled) {
            Log.d(TAG, "VPN filter is off — no restart needed")
            return Result.success()
        }

        // If keep-alive is disabled, skip
        if (!prefs.keepVpnAlive) {
            Log.d(TAG, "Keep VPN alive is off — skipping")
            return Result.success()
        }

        // Check if service is already running
        if (DnsVpnService.serviceRunning) {
            Log.d(TAG, "✅ DnsVpnService is running — no action needed")
            return Result.success()
        }

        // Service is NOT running — check if it was killed unexpectedly
        // We use a simple SharedPrefs heartbeat here since Room requires
        // a fully initialised database (which needs Hilt/manual wiring).
        // When Room is wired up, swap this for the VpnHeartBeatDao query.
        val lastHeartbeatType = prefs.lastVpnHeartbeatType
        Log.d(TAG, "Last heartbeat: $lastHeartbeatType | service running: false")

        if (lastHeartbeatType == VpnHeartBeatEntity.TYPE_ALIVE) {
            Log.w(TAG, "⚠️ VPN was killed unexpectedly — restarting")
            restartVpn()
        } else {
            Log.d(TAG, "VPN was stopped cleanly — no restart")
        }

        return Result.success()
    }

    private fun restartVpn() {
        try {
            val intent = Intent(context, DnsVpnService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "✅ DnsVpnService restarted by heartbeat watchdog")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart DnsVpnService from watchdog", e)
        }
    }
}