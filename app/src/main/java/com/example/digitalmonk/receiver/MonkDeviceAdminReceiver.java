package com.example.digitalmonk.receiver;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

/**
 * Why we made this file:
 * To build a robust parental control app like Digital Monk, you need to prevent
 * the child from simply uninstalling the application. Android provides the
 * "Device Administrator" API for this exact purpose.
 *
 * This Receiver handles the callbacks from the Android system when Device Admin
 * rights are granted, revoked, or requested to be revoked. We also use this class
 * to house the logic for checking admin status and locking the device screen.
 *
 * What the file name defines:
 * "Monk" is the project identifier.
 * "DeviceAdminReceiver" is the specific Android framework component being extended.
 */
public class MonkDeviceAdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "DeviceAdminReceiver";

    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        Log.i(TAG, "✅ Device admin ENABLED — anti-uninstall active");
        Toast.makeText(context, "Digital Monk protection enabled \uD83D\uDEE1\uFE0F", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(@NonNull Context context, @NonNull Intent intent) {
        Log.w(TAG, "⚠️ Device admin DISABLED — uninstall protection removed");
    }

    /**
     * Called when someone tries to deactivate device admin.
     * Return a warning message — Android shows it in the deactivation dialog.
     */
    @Override
    public CharSequence onDisableRequested(@NonNull Context context, @NonNull Intent intent) {
        Log.w(TAG, "Deactivation requested");
        return "⚠️ Disabling this will remove all parental controls and allow this app to be uninstalled. " +
                "A parent PIN is required to do this.";
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onPasswordChanged(@NonNull Context context, @NonNull Intent intent) {
        Log.d(TAG, "Device password changed");
    }

    // ── Static Helper Methods (Formerly Companion Object) ─────────────────────

    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context, MonkDeviceAdminReceiver.class);
    }

    public static boolean isAdminActive(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(getComponentName(context));
    }

    /**
     * Returns an Intent to launch the device admin activation screen.
     * Use this with startActivityForResult() in PermissionSetupScreen.
     */
    public static Intent buildActivationIntent(Context context) {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context));
        intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Digital Monk needs Device Admin rights to prevent children from uninstalling this app. " +
                        "Your parent PIN will be required to remove this protection."
        );
        return intent;
    }

    /**
     * Locks the screen immediately.
     * Call this when a severe bypass attempt is detected.
     */
    public static void lockScreen(Context context) {
        if (!isAdminActive(context)) return;

        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null) {
            dpm.lockNow();
            Log.i(TAG, "Screen locked by Digital Monk");
        }
    }
}