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
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.digitalmonk.core.utils.Constants;

/**
 * Overlay that covers dangerous Android Settings pages (App Info / Device Admin)
 * to prevent uninstall or deactivation of Digital Monk.
 *
 * Shows a full-screen block with a single "Go to Home" button.
 * Dismisses itself automatically when the user leaves the dangerous page.
 */
public class SettingsBlockOverlayService extends Service {

    private static final String TAG = "SettingsBlockOverlay";

    public static final String ACTION_SHOW = "ACTION_SETTINGS_BLOCK_SHOW";
    public static final String ACTION_HIDE = "ACTION_SETTINGS_BLOCK_HIDE";

    public static volatile boolean isRunning = false;

    private WindowManager windowManager;
    private View overlayView;

    // ── Static helpers ────────────────────────────────────────────────────────

    public static void show(Context context) {
        if (isRunning) return; // Already showing — don't stack
        Intent intent = new Intent(context, SettingsBlockOverlayService.class);
        intent.setAction(ACTION_SHOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (ACTION_HIDE.equals(intent.getAction())) {
            removeOverlay();
            stopForeground(true);
            stopSelf();
            isRunning = false;
            return START_NOT_STICKY;
        }

        if (ACTION_SHOW.equals(intent.getAction())) {
            startForeground(Constants.NOTIFICATION_ID_OVERLAY, buildNotification());
            if (overlayView == null) {
                showOverlay();
            }
            isRunning = true;
        }

        return START_NOT_STICKY; // Don't restart — only show when explicitly triggered
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeOverlay();
        isRunning = false;
    }

    // ── Overlay UI ────────────────────────────────────────────────────────────

    private void showOverlay() {
        if (!Settings.canDrawOverlays(this)) return;

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        overlayView = buildView();

        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to add settings block overlay", e);
        }
    }

    private View buildView() {
        // Root container
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#F0080E1A")); // ~94% opaque dark navy

        // Shield emoji
        TextView shield = new TextView(this);
        shield.setText("🛡️");
        shield.setTextSize(64f);
        shield.setGravity(Gravity.CENTER);

        // Title
        TextView title = new TextView(this);
        title.setText("Protected by Digital Monk");
        title.setTextSize(22f);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(48, 24, 48, 12);

        // Subtitle
        TextView subtitle = new TextView(this);
        subtitle.setText("This page is restricted.\nA parent PIN is required to make changes here.");
        subtitle.setTextSize(15f);
        subtitle.setTextColor(Color.parseColor("#94A3B8"));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(48, 0, 48, 48);
        subtitle.setLineSpacing(6f, 1f);

        // Home button
        Button homeBtn = new Button(this);
        homeBtn.setText("← Go to Home Screen");
        homeBtn.setTextColor(Color.WHITE);
        homeBtn.setTextSize(16f);
        homeBtn.setTypeface(null, Typeface.BOLD);
        homeBtn.setBackgroundColor(Color.parseColor("#3B82F6")); // Blue
        homeBtn.setPadding(64, 28, 64, 28);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.gravity = Gravity.CENTER_HORIZONTAL;
        btnParams.topMargin = 8;

        homeBtn.setOnClickListener(v -> {
            // 1. Mark as not running immediately to prevent re-triggering during the exit transition
            isRunning = false;

            // 2. Send user home
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);

            // 3. Stop the service
            hide(getApplicationContext());
        });

        // ── Layout issue: FLAG_NOT_FOCUSABLE blocks button clicks ─────────────
        // Solution: use a Handler to update the params to allow touches
        // right after the view is attached, then revert on hide.
        new Handler(Looper.getMainLooper()).post(() -> {
            if (overlayView != null && windowManager != null) {
                try {
                    WindowManager.LayoutParams lp = (WindowManager.LayoutParams) overlayView.getLayoutParams();
                    // Remove NOT_FOCUSABLE so the button can receive click events
                    lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                    lp.dimAmount = 0.0f;
                    windowManager.updateViewLayout(overlayView, lp);
                } catch (Exception ignored) {}
            }
        });

        root.addView(shield);
        root.addView(title);
        root.addView(subtitle);
        root.addView(homeBtn, btnParams);

        return root;
    }

    private void removeOverlay() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {}
            overlayView = null;
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, Constants.CHANNEL_ALERTS)
                .setContentTitle("Digital Monk — Settings Protected")
                .setContentText("Restricted page blocked")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}