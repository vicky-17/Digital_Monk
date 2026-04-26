package com.example.digitalmonk.service.overlay;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.digitalmonk.service.notification.NotificationHelper;

/**
 * Why we made this file:
 * This Service manages the lifecycle of our system overlays. It listens for
 * command Intents (like "SHOW" or "HIDE") and delegates the actual UI rendering
 * to the BlockOverlayView.
 *
 * Running this as a Foreground Service ensures that Android doesn't kill the
 * overlay memory while the child is staring at the blocked screen.
 *
 * What the file name defines:
 * "Overlay" specifies the feature domain.
 * "Service" identifies it as a long-running Android OS component.
 */
public class OverlayService extends Service {

    private static final String TAG = "OverlayService";

    // Intent Actions & Extras
    public static final String ACTION_SHOW_BLOCK = "ACTION_SHOW_BLOCK";
    public static final String ACTION_HIDE_BLOCK = "ACTION_HIDE_BLOCK";
    public static final String EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME";

    private BlockOverlayView blockOverlayView;

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize the view controller when the service starts
        blockOverlayView = new BlockOverlayView(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Promote to Foreground Service immediately to prevent crash on Android 8.0+
        startForeground(NotificationHelper.FOREGROUND_SERVICE_ID,
                NotificationHelper.buildGuardianForegroundNotification(this));

        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();

            if (ACTION_SHOW_BLOCK.equals(action)) {
                String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
                if (packageName == null) packageName = "Restricted App";

                Log.d(TAG, "Command received: SHOW block for " + packageName);
                blockOverlayView.show(packageName);

            } else if (ACTION_HIDE_BLOCK.equals(action)) {
                Log.d(TAG, "Command received: HIDE block");
                blockOverlayView.hide();
                // Stop the service completely to free up RAM when the overlay isn't needed
                stopSelf();
            }
        }

        // If the system kills this service to reclaim memory, DO NOT automatically restart it.
        // We only want the overlay showing when explicitly triggered by a bypass attempt.
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // This is a Started Service, not a Bound Service
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Failsafe: Ensure the overlay is removed if the service is destroyed
        if (blockOverlayView != null) {
            blockOverlayView.hide();
        }
    }

    // ── Static Helper Methods (Intent Builders) ───────────────────────────────

    /**
     * Triggers the service to show the block overlay.
     */
    public static void showBlockOverlay(Context context, String packageName) {
        Intent intent = new Intent(context, OverlayService.class);
        intent.setAction(ACTION_SHOW_BLOCK);
        intent.putExtra(EXTRA_PACKAGE_NAME, packageName);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * Triggers the service to hide the block overlay and shut itself down.
     */
    public static void hideBlockOverlay(Context context) {
        Intent intent = new Intent(context, OverlayService.class);
        intent.setAction(ACTION_HIDE_BLOCK);
        context.startService(intent); // startService is fine here, it will hit onStartCommand and stopSelf()
    }
}