package com.digitalmonk.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.digitalmonk.app.data.local.prefs.PrefsManager;
import com.digitalmonk.app.service.WatchdogService;
import com.digitalmonk.app.service.vpn.DnsVpnService;
import com.digitalmonk.app.core.utils.AlarmScheduler;

public class AlarmRestartReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmRestartReceiver";
    public static final String ACTION_ALARM_RESTART = "com.example.digitalmonk.ALARM_RESTART";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Alarm fired — reviving services");

        PrefsManager prefs = new PrefsManager(context);
        if (!prefs.hasPin()) return;

        // Restart Watchdog
        WatchdogService.start(context);

        // Restart VPN if needed
        if (prefs.isSafeSearchEnabled() && !DnsVpnService.isServiceRunning) {
            try {
                Intent vpn = new Intent(context, DnsVpnService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(vpn);
                } else {
                    context.startService(vpn);
                }
            } catch (Exception e) {
                Log.e(TAG, "VPN restart failed", e);
            }
        }

        // Re-schedule next alarm immediately (keeps the chain alive)
        AlarmScheduler.scheduleRepeating(context);
    }
}