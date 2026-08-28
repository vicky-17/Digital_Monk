package com.digitalmonk.app.core.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.digitalmonk.app.receiver.AlarmRestartReceiver

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    // 3 minutes — aggressive enough for MIUI
    private val INTERVAL_MS = 3 * 60 * 1000L

    @JvmStatic
    fun scheduleRepeating(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        if (alarmManager == null) return

        val pi = buildPendingIntent(context)
        val triggerAt = System.currentTimeMillis() + INTERVAL_MS

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    Log.i(TAG, "Alarm scheduled (inexact fallback) in 3 min")
                    return
                }
            }
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            Log.i(TAG, "Alarm scheduled in 3 min")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm", e)
        }
    }

    //    @SuppressWarnings("unused")
    //    public static void cancel(Context context) {
    //        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    //        if (alarmManager != null) {
    //            alarmManager.cancel(buildPendingIntent(context));
    //        }
    //    }
    private fun buildPendingIntent(context: Context?): PendingIntent {
        val intent = Intent(context, AlarmRestartReceiver::class.java)
        intent.setAction(AlarmRestartReceiver.ACTION_ALARM_RESTART)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}