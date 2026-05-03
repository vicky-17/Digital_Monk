package com.example.digitalmonk.service.overlay;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;

public class SettingsBlockOverlayService extends Service {

    // Static variables to track state
    public static boolean isRunning = false;
    public static boolean isFullOverlay = false;

    // Static helper to show the bottom strip
    public static void showBottom(Context context) {
        Intent intent = new Intent(context, SettingsBlockOverlayService.class);
        intent.setAction("ACTION_SHOW_BOTTOM");
        context.startService(intent);
    }

    // Static helper to shrink to bottom
    public static void shrinkToBottom(Context context) {
        Intent intent = new Intent(context, SettingsBlockOverlayService.class);
        intent.setAction("ACTION_SHRINK");
        context.startService(intent);
    }

    // Static helper to hide everything
    public static void hide(Context context) {
        Intent intent = new Intent(context, SettingsBlockOverlayService.class);
        context.stopService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "ACTION_SHOW_BOTTOM":
                    isFullOverlay = false;
                    // TODO: Implement window manager logic to show small strip
                    break;
                case "ACTION_SHRINK":
                    isFullOverlay = false;
                    // TODO: Implement logic to change layout params to small
                    break;
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        isFullOverlay = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}