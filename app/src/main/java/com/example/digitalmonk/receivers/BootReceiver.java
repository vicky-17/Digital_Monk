package com.example.digitalmonk.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.digitalmonk.data.PrefsManager;
import com.example.digitalmonk.services.ServiceRestartJob;
import com.example.digitalmonk.services.SyncService;

/**
 * BootReceiver - Auto-starts SyncService when device boots
 * Also handles app restarts after crashes or force stops
 */
public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "Received broadcast: " + action);

        // Check if device is paired before starting service
        PrefsManager prefs = new PrefsManager(context);
        if (!prefs.isPaired()) {
            Log.d(TAG, "Device not paired yet. Skipping service start.");
            return;
        }

        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
                Log.d(TAG, "Device booted. Starting SyncService...");
                startSyncService(context);
                break;

            case Intent.ACTION_MY_PACKAGE_REPLACED:
                Log.d(TAG, "App updated. Restarting SyncService...");
                startSyncService(context);
                break;

            case Intent.ACTION_LOCKED_BOOT_COMPLETED:
                Log.d(TAG, "Direct boot completed. Starting SyncService...");
                startSyncService(context);
                break;

            case "android.intent.action.QUICKBOOT_POWERON":
                Log.d(TAG, "Quick boot detected. Starting SyncService...");
                startSyncService(context);
                break;
                
            case "RESTART_SERVICE":
                Log.d(TAG, "Service restart triggered. Starting SyncService...");
                startSyncService(context);
                break;
        }
    }

    /**
     * Starts SyncService in foreground mode
     */
    private void startSyncService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, SyncService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            
            // Also schedule JobService for periodic checks
            ServiceRestartJob.scheduleJob(context);
            
            Log.d(TAG, "✅ SyncService started successfully + JobService scheduled");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to start SyncService", e);
        }
    }
}
