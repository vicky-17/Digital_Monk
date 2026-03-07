package com.example.digitalmonk.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Device Admin Receiver — enables powerful security policies.
 *
 * ── What Device Admin unlocks ────────────────────────────────────────────────
 *
 * 1. ANTI-UNINSTALL PROTECTION
 *    When the user tries to uninstall Digital Monk, Android shows a warning:
 *    "This app is a device administrator. You must deactivate it before uninstalling."
 *    The child must go to Settings → Security → Device Admins → Digital Monk → Deactivate.
 *    This extra step is a strong deterrent. We can intercept the deactivation attempt
 *    in onDisableRequested() and require the parent PIN before allowing it.
 *
 * 2. FORCE LOCK SCREEN
 *    We can lock the device immediately when a blocked app is detected.
 *    (Better UX than just sending HOME — the child can't bypass by switching apps)
 *
 * 3. PASSWORD POLICIES (Enterprise/MDM use)
 *    Can enforce minimum PIN length, screen lock timeout, etc.
 *
 * ── How to activate ──────────────────────────────────────────────────────────
 *    In PermissionSetupScreen, we launch:
 *    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
 *    The user sees an explanation and taps "Activate" — that's it.
 *
 * ── How to check if active ───────────────────────────────────────────────────
 *    DevicePolicyManager.isAdminActive(ComponentName)
 */
class MonkDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "✅ Device admin ENABLED — anti-uninstall active")
        Toast.makeText(context, "Digital Monk protection enabled 🛡️", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w(TAG, "⚠️ Device admin DISABLED — uninstall protection removed")
    }

    /**
     * Called when someone tries to deactivate device admin.
     * Return a warning message — Android shows it in the deactivation dialog.
     * The child will see this message and (hopefully) give up.
     *
     * Note: We cannot BLOCK deactivation here — only show a warning.
     * The PIN gate to deactivation is handled in PermissionSetupScreen.
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "Deactivation requested")
        return "⚠️ Disabling this will remove all parental controls and allow this app to be uninstalled. " +
                "A parent PIN is required to do this."
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        Log.d(TAG, "Device password changed")
    }

    companion object {
        private const val TAG = "DeviceAdminReceiver"

        fun getComponentName(context: Context): ComponentName =
            ComponentName(context, MonkDeviceAdminReceiver::class.java)

        fun isAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isAdminActive(getComponentName(context))
        }

        /**
         * Returns an Intent to launch the device admin activation screen.
         * Use this with startActivityForResult() in PermissionSetupScreen.
         */
        fun buildActivationIntent(context: Context): Intent {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Digital Monk needs Device Admin rights to prevent children from uninstalling this app. " +
                            "Your parent PIN will be required to remove this protection."
                )
            }
        }

        /**
         * Locks the screen immediately.
         * Call this when a severe bypass attempt is detected.
         */
        fun lockScreen(context: Context) {
            if (!isAdminActive(context)) return
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.lockNow()
            Log.i(TAG, "Screen locked by Digital Monk")
        }
    }
}