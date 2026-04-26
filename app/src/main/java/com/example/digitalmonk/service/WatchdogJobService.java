package com.example.digitalmonk.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.util.Log;

/**
 * Why we made this file:
 * While WorkManager (used in your other Workers) is the modern standard, JobScheduler
 * is the underlying system API that Android uses to guarantee task execution.
 * * For a parental control app, "resilience" is everything. This JobService acts as
 * the "Last Line of Defense." Even if the child manages to "Force Stop" the app
 * or the phone reboots, the Android OS itself holds this job in its persistent
 * queue and will wake the app up every 15 minutes to ensure the WatchdogService
 * is back online.
 *
 * What the file name defines:
 * "Watchdog" refers to its purpose (monitoring other processes).
 * "JobService" identifies it as a component managed by the Android JobScheduler.
 */
public class WatchdogJobService extends JobService {

    private static final String TAG = "WatchdogJobService";

    /**
     * Called by the Android system when the job is triggered.
     * This runs on the Main Thread, so we keep the logic very light.
     */
    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "Watchdog job fired — checking services");

        try {
            // We call the static start method of your WatchdogService.
            // This ensures that the core background monitor is always running.
            WatchdogService.start(getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart services from job", e);
        }

        // Return false means the job has finished its work.
        // We call jobFinished to inform the system we are done.
        jobFinished(params, false);
        return false;
    }

    /**
     * Called if the system must stop the job before jobFinished() is called
     * (e.g., if the phone's resources are too low).
     */
    @Override
    public boolean onStopJob(JobParameters params) {
        Log.w(TAG, "Watchdog job stopped prematurely by system");

        // Return true means "Yes, please reschedule this job."
        // This is critical for maintaining the safety loop.
        return true;
    }
}