package com.example.digitalmonk.receivers;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.example.digitalmonk.data.PrefsManager;
import com.example.digitalmonk.services.SyncService;

public class MyAppAdminReceiver extends DeviceAdminReceiver {
    
    private static final String TAG = "MyAppAdminReceiver";
    
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Toast.makeText(context, "G4 Security Enabled", Toast.LENGTH_SHORT).show();
        
        // Start SyncService immediately when admin is enabled
        startSyncService(context);
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Toast.makeText(context, "G4 Security Disabled", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);
        
        // Check if device is paired
        PrefsManager prefs = new PrefsManager(context);
        if (!prefs.isPaired()) {
            return;
        }
        
        // Start service on any device admin event
        if (action != null) {
            startSyncService(context);
        }
    }
    
    /**
     * Ensures SyncService is always running
     */
    private void startSyncService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, SyncService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            
            Log.d(TAG, "✅ SyncService started from DeviceAdmin");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to start SyncService", e);
        }
    }
}