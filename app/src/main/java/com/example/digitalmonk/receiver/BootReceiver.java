package com.example.digitalmonk.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.WatchdogService;
import com.example.digitalmonk.service.vpn.DnsVpnService;

/**
 * Why we made this file:
 * When an Android device reboots, standard background and foreground services
 * do NOT automatically restart. For a parental control app, a child rebooting
 * the phone is a common tactic used to try and bypass restrictions.
 *
 * This BroadcastReceiver listens for the system's "BOOT_COMPLETED" broadcast
 * and immediately turns the Watchdog and VPN services back on, ensuring
 * the protection remains active without the parent needing to open the app.
 *
 * What the file name defines:
 * "Boot" refers to the device startup process.
 * "Receiver" identifies the Android component (BroadcastReceiver).
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Safe check for null intent and action in Java
        if (intent == null) return;

        String action = intent.getAction();
        if (action == null) return;

        // Using .equals() for safe string comparison to prevent NullPointerExceptions
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) &&
                !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) &&
                !"android.intent.action.QUICKBOOT_POWERON".equals(action) &&  // HTC/some Chinese OEMs
                !"com.htc.intent.action.QUICKBOOT_POWERON".equals(action)     // HTC variant
        ) {
            return;
        }

        Log.i(TAG, "Boot completed — starting Digital Monk services");

        PrefsManager prefs = new PrefsManager(context);

        // Only start services if the app was set up (has a PIN)
        if (!prefs.hasPin()) {
            Log.i(TAG, "App not set up yet — skipping service start");
            return;
        }

        // 1. Always start WatchdogService — it's the root guardian
        WatchdogService.start(context);

        // 2. Restart VPN if it was active before reboot
        if (prefs.isSafeSearchEnabled()) {
            Log.i(TAG, "Restarting DnsVpnService (was active before reboot)");
            try {
                Intent vpnIntent = new Intent(context, DnsVpnService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(vpnIntent);
                } else {
                    context.startService(vpnIntent);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to restart DnsVpnService on boot", e);
                // WatchdogService will retry in its health check loop
            }
        }

        // 3. Re-schedule the JobScheduler backup
        WatchdogService.scheduleJobBackup(context);

        Log.i(TAG, "✅ All services started after boot");
    }
}