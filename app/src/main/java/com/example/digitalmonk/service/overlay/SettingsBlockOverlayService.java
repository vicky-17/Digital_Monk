package com.example.digitalmonk.service.overlay;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.compose.runtime.MutableState;
import androidx.core.app.NotificationCompat;

import com.example.digitalmonk.core.utils.Constants;
import com.example.digitalmonk.ui.overlay.OverlayBridge;
import com.example.digitalmonk.ui.overlay.OverlayLifecycleOwner;
import com.example.digitalmonk.ui.overlay.SettingsOverlayStage;

import androidx.compose.ui.platform.ComposeView;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import androidx.savedstate.ViewTreeSavedStateRegistryOwner;



public class SettingsBlockOverlayService extends Service {

    private static final String TAG = "SettingsBlockOverlay";

    public static final String ACTION_SHOW_BOTTOM   = "ACTION_SETTINGS_BLOCK_BOTTOM";
    public static final String ACTION_SHOW_FULL     = "ACTION_SETTINGS_BLOCK_FULL";
    public static final String ACTION_SHRINK_BOTTOM = "ACTION_SETTINGS_BLOCK_SHRINK";
    public static final String ACTION_HIDE          = "ACTION_SETTINGS_BLOCK_HIDE";

    public static volatile boolean isRunning     = false;
    public static volatile boolean isFullOverlay = false;

    private WindowManager windowManager;
    private ComposeView   composeView;
    private WindowManager.LayoutParams overlayParams;
    private OverlayLifecycleOwner lifecycleOwner;
    private Handler mainHandler;





    // ── Static helpers ────────────────────────────────────────────────────────

    public static void showBottom(Context context) {
        Intent i = new Intent(context, SettingsBlockOverlayService.class);
        i.setAction(ACTION_SHOW_BOTTOM);
        context.startForegroundService(i);
    }

    public static void expandFull(Context context) {
        Intent i = new Intent(context, SettingsBlockOverlayService.class);
        i.setAction(ACTION_SHOW_FULL);
        context.startForegroundService(i);
    }

    public static void shrinkToBottom(Context context) {
        Intent i = new Intent(context, SettingsBlockOverlayService.class);
        i.setAction(ACTION_SHRINK_BOTTOM);
        context.startForegroundService(i);
    }

    public static void hide(Context context) {
        Intent i = new Intent(context, SettingsBlockOverlayService.class);
        i.setAction(ACTION_HIDE);
        context.startService(i);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mainHandler   = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }

        try {
            startForeground(Constants.NOTIFICATION_ID_SETTINGS_BLOCK, buildNotification());
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
        }

        String action = intent.getAction();
        if (action == null) { stopSelf(); return START_NOT_STICKY; }

        // If full overlay is locked, only allow HIDE
        if (isFullOverlay && !ACTION_HIDE.equals(action)) {
            return START_NOT_STICKY;
        }

        switch (action) {

            case ACTION_SHOW_BOTTOM:
                if (!isRunning) {
                    isRunning = true;
                    // Show the overlay at HALF stage (650dp initial blocker)
                    mainHandler.post(() -> {
                        showOverlay();
                        updateStage(SettingsOverlayStage.HALF);
                    });
                }
                break;

            case ACTION_SHOW_FULL:
                isFullOverlay = true;
                mainHandler.post(() -> {
                    if (!isRunning) showOverlay();
                    updateStage(SettingsOverlayStage.FULL);
                    // Make overlay interactive so "Go Home" button works
                    if (composeView != null) {
                        overlayParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
                        try { windowManager.updateViewLayout(composeView, overlayParams); } catch (Exception ignored) {}
                    }
                });
                break;

            case ACTION_SHRINK_BOTTOM:
                mainHandler.postDelayed(() -> {
                    if (!isFullOverlay && isRunning) {
                        updateStage(SettingsOverlayStage.STRIP);
                    }
                }, 2000L);
                break;

            case ACTION_HIDE:
                mainHandler.post(() -> {
                    removeOverlay();
                    stopForeground(true);
                    stopSelf();
                    isRunning     = false;
                    isFullOverlay = false;
                });
                break;
        }
        return START_NOT_STICKY;
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        removeOverlay();
        isRunning     = false;
        isFullOverlay = false;
    }

    // ── Core: show the ComposeView overlay once ───────────────────────────────

    private void showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "No overlay permission");
            return;
        }
        if (composeView != null) return; // already showing

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.BOTTOM | Gravity.START;

        // Set up Compose lifecycle so ComposeView renders outside an Activity
        lifecycleOwner = new OverlayLifecycleOwner();
        lifecycleOwner.onCreate();

        composeView = new ComposeView(this);

        // Attach lifecycle and saved-state owners (required by Compose)
        ViewTreeLifecycleOwner.set(composeView, lifecycleOwner);
        ViewTreeSavedStateRegistryOwner.set(composeView, lifecycleOwner);

        // Delegate content to the Kotlin bridge
        OverlayBridge.setContent(composeView, () -> {
            // "Go Home" callback
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
            isFullOverlay = false;
            hide(this);
        });

        try {
            windowManager.addView(composeView, overlayParams);
            lifecycleOwner.onStart();
            lifecycleOwner.onResume();
            Log.i(TAG, "Compose overlay added");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add overlay", e);
            composeView = null;
        }
    }

    // ── Single method to change stage — this is all WatchdogService calls ─────

    private void updateStage(SettingsOverlayStage stage) {
        OverlayBridge.setStage(stage);
        Log.d(TAG, "Overlay stage → " + stage.name());
    }

    private void removeOverlay() {
        if (lifecycleOwner != null) {
            lifecycleOwner.onPause();
            lifecycleOwner.onStop();
            lifecycleOwner.onDestroy();
            lifecycleOwner = null;
        }
        if (composeView != null && windowManager != null) {
            try { windowManager.removeView(composeView); } catch (Exception ignored) {}
            composeView = null;
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, Constants.CHANNEL_SILENT)
                .setContentTitle("").setContentText("")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setSilent(true).setShowWhen(false).setOngoing(false)
                .build();
    }
}