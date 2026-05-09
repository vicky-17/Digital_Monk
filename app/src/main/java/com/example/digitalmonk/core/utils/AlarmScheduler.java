package com.example.digitalmonk.core.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.digitalmonk.receiver.AlarmRestartReceiver;

public class AlarmScheduler {

    private static final String TAG = "AlarmScheduler";
    // 3 minutes — aggressive enough for MIUI
    private static final long INTERVAL_MS = 3 * 60 * 1000L;

    public static void scheduleRepeating(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent pi = buildPendingIntent(context);
        long triggerAt = System.currentTimeMillis() + INTERVAL_MS;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                    Log.i(TAG, "Alarm scheduled (inexact fallback) in 3 min");
                    return;
                }
            }
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            Log.i(TAG, "Alarm scheduled in 3 min");
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule alarm", e);
        }
    }

//    @SuppressWarnings("unused")
//    public static void cancel(Context context) {
//        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
//        if (alarmManager != null) {
//            alarmManager.cancel(buildPendingIntent(context));
//        }
//    }

    private static PendingIntent buildPendingIntent(Context context) {
        Intent intent = new Intent(context, AlarmRestartReceiver.class);
        intent.setAction(AlarmRestartReceiver.ACTION_ALARM_RESTART);
        return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}