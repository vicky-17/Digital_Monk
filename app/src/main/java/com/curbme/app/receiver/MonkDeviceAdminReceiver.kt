package com.curbme.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

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
class MonkDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "✅ Device admin ENABLED — anti-uninstall active")
        Toast.makeText(
            context,
            "CurbMe protection enabled \uD83D\uDEE1\uFE0F",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w(TAG, "⚠️ Device admin DISABLED — uninstall protection removed")
    }

    /**
     * Called when someone tries to deactivate device admin.
     * Return a warning message — Android shows it in the deactivation dialog.
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        Log.w(TAG, "Deactivation requested")
        return "⚠️ Disabling this will remove all parental controls and allow this app to be uninstalled. " +
                "A parent PIN is required to do this."
    }

    @Suppress("deprecation")
    override fun onPasswordChanged(context: Context, intent: Intent) {
        Log.d(TAG, "Device password changed")
    }

    companion object {
        private const val TAG = "DeviceAdminReceiver"

        // ── Static Helper Methods (Formerly Companion Object) ─────────────────────
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, MonkDeviceAdminReceiver::class.java)
        }

        fun isAdminActive(context: Context): Boolean {
            val dpm =
                context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?
            return dpm != null && dpm.isAdminActive(getComponentName(context))
        }

        /**
         * Returns an Intent to launch the device admin activation screen.
         * Use this with startActivityForResult() in PermissionSetupScreen.
         */
        fun buildActivationIntent(context: Context): Intent {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
            intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "CurbMe needs Device Admin rights to prevent children from uninstalling this app. " +
                        "Your parent PIN will be required to remove this protection."
            )
            return intent
        }

        /**
         * Locks the screen immediately.
         * Call this when a severe bypass attempt is detected.
         */
        fun lockScreen(context: Context) {
            if (!isAdminActive(context)) return

            val dpm =
                context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?
            if (dpm != null) {
                dpm.lockNow()
                Log.i(TAG, "Screen locked by CurbMe")
            }
        }
    }
}