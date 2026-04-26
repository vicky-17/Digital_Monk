package com.example.digitalmonk.service.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Why we made this file:
 * This class handles the creation and destruction of a "System Alert Window" (Overlay).
 * It draws a blocking UI directly over the entire screen, regardless of what app
 * is currently running in the foreground.
 *
 * Note: This requires the user to grant the "Display over other apps"
 * (SYSTEM_ALERT_WINDOW) permission in Android settings.
 *
 * What the file name defines:
 * "Block" indicates the purpose of the UI.
 * "OverlayView" defines how it is rendered (on top of the OS via WindowManager).
 */
public class BlockOverlayView {

    private static final String TAG = "BlockOverlayView";

    private final Context context;
    private final WindowManager windowManager;
    private View overlayView;

    public BlockOverlayView(Context context) {
        // Prevent memory leaks by using the Application Context!
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    /**
     * Draws the blocking UI over the screen.
     * * @param blockedAppName The name of the app that was blocked (to show the child).
     */
    public void show(String blockedAppName) {
        // Prevent drawing multiple overlays if one is already showing
        if (overlayView != null) {
            return;
        }

        if (windowManager == null) return;

        try {
            // 1. Create the View (Using programmatic UI here so you don't need an XML file immediately,
            // but in production, you should use LayoutInflater to inflate an XML layout).
            overlayView = createProgrammaticView(blockedAppName);

            // 2. Configure the WindowManager LayoutParams
            int layoutFlag;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+ requires TYPE_APPLICATION_OVERLAY
                layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                // Fallback for older devices (deprecated but necessary)
                layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
            }

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutFlag,
                    // These flags ensure the overlay covers everything and the user
                    // cannot touch the app underneath it.
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
            );

            // Center the view
            params.gravity = Gravity.CENTER;

            // 3. Attach the view to the screen
            windowManager.addView(overlayView, params);
            Log.i(TAG, "Overlay shown for: " + blockedAppName);

        } catch (Exception e) {
            Log.e(TAG, "Failed to show block overlay. Does the app have SYSTEM_ALERT_WINDOW permission?", e);
        }
    }

    /**
     * Removes the blocking UI from the screen.
     * This MUST be called to prevent permanently locking the phone!
     */
    public void hide() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
                overlayView = null;
                Log.i(TAG, "Overlay removed.");
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay", e);
            }
        }
    }

    /**
     * Helper method to build a simple UI in pure Java.
     */
    private View createProgrammaticView(String blockedAppName) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#E53935")); // A strong red color

        TextView message = new TextView(context);
        message.setText("🛡️ Digital Monk");
        message.setTextSize(32f);
        message.setTextColor(Color.WHITE);
        message.setPadding(0, 0, 0, 32);

        TextView subMessage = new TextView(context);
        subMessage.setText(blockedAppName + " is blocked by your parents.");
        subMessage.setTextSize(20f);
        subMessage.setTextColor(Color.WHITE);
        subMessage.setGravity(Gravity.CENTER);

        layout.addView(message);
        layout.addView(subMessage);

        return layout;
    }
}