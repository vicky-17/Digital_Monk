package com.example.digitalmonk.service.monitor;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * Why we made this file:
 * While the GuardianAccessibilityService reacts to what the child is doing RIGHT NOW,
 * you also need a way to reliably sync historical screen-time data to your Vercel
 * web dashboard so the parent can view it later.
 * * Android's "WorkManager" is the modern standard for background tasks that must
 * complete reliably, even if the app is closed or the device reboots. This "Worker"
 * wakes up periodically, gathers the app usage data, and safely sends it to your server.
 *
 * What the file name defines:
 * "AppUsage" defines the data being processed.
 * "Worker" identifies it as a WorkManager component.
 */
public class AppUsageWorker extends Worker {

    private static final String TAG = "AppUsageWorker";
    private static final String WORK_NAME = "SyncAppUsageToVercel";

    /**
     * Standard constructor required by the Android WorkManager framework.
     */
    public AppUsageWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * This is the method that runs in the background.
     * Unlike the main thread, it is perfectly safe to do heavy database queries
     * or HTTP POST requests to your Vercel app right here.
     */
    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "Starting periodic App Usage sync to Web Dashboard...");

        try {
            // TODO Phase 3: Implement data extraction and syncing
            // 1. Fetch data from local Room database or UsageStatsManager
            //    List<UsageLog> usageLogs = usageRepository.getUnsyncedLogs();

            // 2. Convert to JSON (using Gson or Moshi)
            //    String jsonPayload = gson.toJson(usageLogs);

            // 3. Make HTTP POST request to your Vercel Web App API
            //    boolean success = apiClient.postUsageData(jsonPayload);

            // 4. If successful, mark local logs as "synced" in Room DB so they
            //    aren't sent twice.

            Log.i(TAG, "Successfully synced usage data.");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Error syncing usage data to Vercel", e);
            // If the network drops or the server 500s, tell WorkManager to try again later!
            return Result.retry();
        }
    }

    /**
     * Static helper method to schedule this background job.
     * You should call this from your BootReceiver or MainActivity once setup is complete.
     */
    public static void schedule(Context context) {
        // Only run this sync if the phone has an active internet connection
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Schedule it to run approximately every 15 minutes
        // (15 mins is the strict minimum allowed by Android WorkManager)
        PeriodicWorkRequest syncRequest = new PeriodicWorkRequest.Builder(
                AppUsageWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        // Enqueue unique work prevents multiple identical timers from running simultaneously
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // If it's already scheduled, leave it alone
                syncRequest
        );

        Log.d(TAG, "AppUsageWorker scheduled to run every 15 minutes.");
    }
}