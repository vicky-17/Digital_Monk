package com.example.digitalmonk.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class BlockOverlay {

    private final Context context;
    private final WindowManager windowManager;
    private View overlayView;
    private boolean isShowing = false;
    private long snoozeUntil = 0; // Timestamp to keep overlay hidden (for user to fix settings)

    public BlockOverlay(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show() {
        if (isShowing) return;
        if (System.currentTimeMillis() < snoozeUntil) return; // Don't show if snoozed

        // Check permission
        if (!Settings.canDrawOverlays(context)) return;

        // Create the UI programmatically (No XML needed)
        overlayView = createOverlayView();

        // Setup Layout Params
        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                // FLAG_NOT_TOUCH_MODAL allows touches to go to system bars (optional)
                // FLAG_LAYOUT_IN_SCREEN makes it cover status bar
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_FULLSCREEN |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // Needed to allow system key navigation if needed, but we block touches
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;

        try {
            windowManager.addView(overlayView, params);
            isShowing = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void hide() {
        if (!isShowing || overlayView == null) return;
        try {
            windowManager.removeView(overlayView);
            isShowing = false;
            overlayView = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Hides the overlay for a few seconds to let the user open Settings.
     */
    private void snooze(long durationMs) {
        hide();
        snoozeUntil = System.currentTimeMillis() + durationMs;
    }

    private View createOverlayView() {
        // Root Layout (Black Background)
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setGravity(Gravity.CENTER);
        root.setPadding(50, 50, 50, 50);

        // Title
        TextView title = new TextView(context);
        title.setText("⚠️ DEVICE LOCKED ⚠️");
        title.setTextColor(Color.RED);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 40);
        root.addView(title);

        // Message
        TextView msg = new TextView(context);
        msg.setText("Internet and Location are REQUIRED.\nThe device is locked until they are enabled.");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(16);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, 0, 0, 60);
        root.addView(msg);

        // Button: Open Settings
        Button btnSettings = new Button(context);
        btnSettings.setText("OPEN SETTINGS");
        btnSettings.setBackgroundColor(Color.DKGRAY);
        btnSettings.setTextColor(Color.WHITE);
        btnSettings.setOnClickListener(v -> {
            snooze(15000); // Hide for 15 seconds
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ignored) {}
        });
        root.addView(btnSettings);

        // Spacer
        View spacer = new View(context);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 40));

        // Button: Open Wi-Fi
        Button btnWifi = new Button(context);
        btnWifi.setText("TURN ON WI-FI");
        btnWifi.setBackgroundColor(Color.BLUE);
        btnWifi.setTextColor(Color.WHITE);
        btnWifi.setOnClickListener(v -> {
            snooze(10000); // Hide for 10 seconds
            try {
                Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ignored) {}
        });
        root.addView(btnWifi);

        return root;
    }
}