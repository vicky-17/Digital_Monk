package com.example.digitalmonk.service

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Backup restart mechanism using JobScheduler.
 *
 * JobScheduler jobs survive reboots (setPersisted=true) and are much harder
 * for OEM memory managers (MIUI, ColorOS, etc.) to kill than plain services.
 *
 * This job fires every 15 minutes and ensures WatchdogService is running.
 * It's the "last line of defense" if everything else was killed.
 */
class WatchdogJobService : JobService() {

    companion object {
        private const val TAG = "WatchdogJobService"
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.i(TAG, "Watchdog job fired — checking services")

        try {
            WatchdogService.start(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart services from job", e)
        }

        // Return false = job is done synchronously, no more work needed
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Return true = reschedule the job if it was stopped prematurely
        return true
    }
}