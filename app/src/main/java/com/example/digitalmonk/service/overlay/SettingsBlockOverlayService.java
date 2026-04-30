package com.example.digitalmonk.service.overlay;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.digitalmonk.R;
import com.example.digitalmonk.core.utils.Constants;

/**
 * FAST DUAL-OVERLAY Anti-Uninstall System
 * ─────────────────────────────────────────────────────────────────────────────
 * Layer 1 — BOTTOM BLOCKER (instant, on settings package open)
 * Layer 2 — FULL SCREEN BLOCK (after 4-gate confirmation)
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class SettingsBlockOverlayService extends Service {

    private static final String TAG = "SettingsBlockOverlay";

    // ── Intent actions ────────────────────────────────────────────────────────
    public static final String ACTION_SHOW_INSTANT = "ACTION_SETTINGS_BLOCK_INSTANT";
    public static final String ACTION_SHOW_FULL    = "ACTION_SETTINGS_BLOCK_SHOW";
    public static final String ACTION_HIDE         = "ACTION_SETTINGS_BLOCK_HIDE";

    // ── State ─────────────────────────────────────────────────────────────────
    public static volatile boolean isRunning     = false;
    public static volatile boolean isFullOverlay = false;

    private WindowManager windowManager;
    private View bottomBlockerView;
    private View fullOverlayView;

    private Handler handler;
    private int screenWidth;
    private int screenHeight;

    // ── Static helpers ────────────────────────────────────────────────────────

    public static void showInstant(Context context) {
        if (isRunning) return;
        Intent intent = new Intent(context, SettingsBlockOverlayService.class);
        intent.setAction(ACTION_SHOW_INSTANT);
        context.startForegroundService(intent);
    }

    public static void show(Context context) {
        Intent intent = new Intent(context, SettingsBlockOverlayService.class);
        intent.setAction(ACTION_SHOW_FULL);
        context.startForegroundService(intent);
    }

    public static void hide(Context context) {
        Intent intent = new Intent(context, SettingsBlockOverlayService.class);
        intent.setAction(ACTION_HIDE);
        context.startService(intent);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());

        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(dm);
        screenWidth  = dm.widthPixels;
        screenHeight = dm.heightPixels;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (action == null) return START_NOT_STICKY;

        switch (action) {

            case ACTION_SHOW_INSTANT:
                startForeground(Constants.NOTIFICATION_ID_OVERLAY, buildNotification());
                isRunning     = true;
                isFullOverlay = false;
                showBottomBlocker();
                scheduleAutoTimeout();
                break;

            case ACTION_SHOW_FULL:
                cancelAutoTimeout();
                isFullOverlay = true;
                removeBottomBlocker();
                if (fullOverlayView == null) {
                    showFullOverlay();
                }
                break;

            case ACTION_HIDE:
                cancelAutoTimeout();
                removeBottomBlocker();
                removeFullOverlay();
                stopForeground(true);
                stopSelf();
                isRunning     = false;
                isFullOverlay = false;
                return START_NOT_STICKY;
        }

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelAutoTimeout();
        removeBottomBlocker();
        removeFullOverlay();
        isRunning     = false;
        isFullOverlay = false;
    }

    // ── Layer 1: Bottom Blocker ───────────────────────────────────────────────

    private void showBottomBlocker() {
        if (bottomBlockerView != null) return;
        if (!Settings.canDrawOverlays(this)) return;

        float density = getResources().getDisplayMetrics().density;
        int blockerHeight = (int)(180 * density);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                blockerHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // API 26+ only, minSdk handles this
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.OPAQUE
        );
        params.gravity = Gravity.BOTTOM | Gravity.START;
        params.x = 0;
        params.y = 0;

        bottomBlockerView = buildBottomBlockerView();

        try {
            windowManager.addView(bottomBlockerView, params);
            android.util.Log.i(TAG, "⚡ Bottom blocker shown instantly");
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to show bottom blocker", e);
            bottomBlockerView = null;
        }
    }

    private View buildBottomBlockerView() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#F0080E1A"));

        TextView label = new TextView(this);
        label.setText(R.string.overlay_protected_label);
        label.setTextSize(13f);
        label.setTextColor(Color.parseColor("#64748B"));
        label.setGravity(Gravity.CENTER);
        label.setTypeface(null, Typeface.BOLD);
        layout.addView(label);

        return layout;
    }

    private void removeBottomBlocker() {
        if (bottomBlockerView != null && windowManager != null) {
            try { windowManager.removeView(bottomBlockerView); } catch (Exception ignored) {}
            bottomBlockerView = null;
        }
    }

    // ── Layer 2: Full Screen Overlay ──────────────────────────────────────────

    private void showFullOverlay() {
        if (!Settings.canDrawOverlays(this)) return;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        fullOverlayView = buildFullView();

        try {
            windowManager.addView(fullOverlayView, params);
            // Allow button clicks after view is attached
            handler.post(() -> {
                if (fullOverlayView != null && windowManager != null) {
                    try {
                        WindowManager.LayoutParams lp =
                                (WindowManager.LayoutParams) fullOverlayView.getLayoutParams();
                        lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
                        windowManager.updateViewLayout(fullOverlayView, lp);
                    } catch (Exception ignored) {}
                }
            });
            android.util.Log.i(TAG, "✅ Full overlay shown");
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to show full overlay", e);
        }
    }

    private View buildFullView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#F0080E1A"));

        TextView shield = new TextView(this);
        shield.setText(R.string.overlay_shield_emoji);
        shield.setTextSize(64f);
        shield.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText(R.string.overlay_protected_title);
        title.setTextSize(22f);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(48, 24, 48, 12);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.overlay_protected_subtitle);
        subtitle.setTextSize(15f);
        subtitle.setTextColor(Color.parseColor("#94A3B8"));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(48, 0, 48, 48);
        subtitle.setLineSpacing(6f, 1f);

        Button homeBtn = new Button(this);
        homeBtn.setText(R.string.overlay_go_home_button);
        homeBtn.setTextColor(Color.WHITE);
        homeBtn.setTextSize(16f);
        homeBtn.setTypeface(null, Typeface.BOLD);
        homeBtn.setBackgroundColor(Color.parseColor("#3B82F6"));
        homeBtn.setPadding(64, 28, 64, 28);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.gravity = Gravity.CENTER_HORIZONTAL;
        btnParams.topMargin = 8;

        homeBtn.setOnClickListener(v -> {
            isRunning     = false;
            isFullOverlay = false;
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
            hide(getApplicationContext());
        });

        root.addView(shield);
        root.addView(title);
        root.addView(subtitle);
        root.addView(homeBtn, btnParams);

        return root;
    }

    private void removeFullOverlay() {
        if (fullOverlayView != null && windowManager != null) {
            try { windowManager.removeView(fullOverlayView); } catch (Exception ignored) {}
            fullOverlayView = null;
        }
    }

    // ── Auto-timeout for bottom blocker ───────────────────────────────────────

    private static final long AUTO_TIMEOUT_MS = 3000L;

    private final Runnable autoTimeoutRunnable = () -> {
        android.util.Log.d(TAG, "Auto-timeout: no dangerous page confirmed, removing bottom blocker");
        removeBottomBlocker();
        if (!isFullOverlay) {
            stopForeground(true);
            stopSelf();
            isRunning = false;
        }
    };

    private void scheduleAutoTimeout() {
        handler.removeCallbacks(autoTimeoutRunnable);
        handler.postDelayed(autoTimeoutRunnable, AUTO_TIMEOUT_MS);
    }

    private void cancelAutoTimeout() {
        handler.removeCallbacks(autoTimeoutRunnable);
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, Constants.CHANNEL_ALERTS)
                .setContentTitle(getString(R.string.overlay_notification_title))
                .setContentText(getString(R.string.overlay_notification_text))
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}