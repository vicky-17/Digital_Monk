package com.example.digitalmonk.service.vpn.heartbeat;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.vpn.DnsVpnService;

import java.util.concurrent.TimeUnit;

/**
 * Why we made this file:
 * Android memory management is aggressive. Even Foreground Services can be killed
 * by heavily customized OEM skins (like Xiaomi's MIUI or Samsung's OneUI).
 * * This WorkManager class acts as the "Watchdog" for the VPN. Because WorkManager
 * is deeply integrated into the Android OS, it survives app swiping and reboots.
 * It wakes up every 15 minutes, checks the "Heartbeat" we defined earlier, and
 * forcefully revives the VPN if it detects the system killed it.
 *
 * What the file name defines:
 * "HeartbeatMonitor" identifies its role in checking the life status of another component.
 * "Worker" identifies it as a WorkManager task.
 */
public class VpnHeartbeatMonitorWorker extends Worker {

    private static final String TAG = "VpnHeartbeatMonitor";
    public static final String WORK_NAME = "vpn_heartbeat_monitor";

    public VpnHeartbeatMonitorWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    // ── Static Helper Methods (Formerly Companion Object) ─────────────────────

    /**
     * Schedules (or re-schedules) the periodic watchdog.
     * Call this from DnsVpnService.startVpn() and from BootReceiver.
     */
    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                VpnHeartbeatMonitorWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // Don't replace if already running
                request
        );
        Log.i(TAG, "Heartbeat monitor scheduled");
    }

    /** Cancel the watchdog — called when VPN is cleanly stopped by the user. */
    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        Log.i(TAG, "Heartbeat monitor cancelled");
    }

    // ── Background Execution ──────────────────────────────────────────────────

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Heartbeat check running…");

        // getApplicationContext() is provided by the Worker superclass
        Context context = getApplicationContext();
        PrefsManager prefs = new PrefsManager(context);

        // If the user has turned off the VPN filter, don't restart
        if (!prefs.isSafeSearchEnabled()) {
            Log.d(TAG, "VPN filter is off — no restart needed");
            return Result.success();
        }

        // If keep-alive is disabled, skip
        if (!prefs.isKeepVpnAlive()) {
            Log.d(TAG, "Keep VPN alive is off — skipping");
            return Result.success();
        }

        // Check if service is already running
        // Note: Make sure DnsVpnService has a static public boolean or getter for this!
        if (DnsVpnService.isServiceRunning) {
            Log.d(TAG, "✅ DnsVpnService is running — no action needed");
            return Result.success();
        }

        // Service is NOT running — check if it was killed unexpectedly
        String lastHeartbeatType = prefs.getLastVpnHeartbeatType();
        Log.d(TAG, "Last heartbeat: " + lastHeartbeatType + " | service running: false");

        if (VpnHeartBeatEntity.TYPE_ALIVE.equals(lastHeartbeatType)) {
            Log.w(TAG, "⚠️ VPN was killed unexpectedly — restarting");
            restartVpn(context);
        } else {
            Log.d(TAG, "VPN was stopped cleanly — no restart");
        }

        return Result.success();
    }

    private void restartVpn(Context context) {
        try {
            Intent intent = new Intent(context, DnsVpnService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            Log.i(TAG, "✅ DnsVpnService restarted by heartbeat watchdog");
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart DnsVpnService from watchdog", e);
        }
    }
}