package com.curbme.app.core.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.curbme.app.receiver.CurbMeDeviceAdminReceiver
import com.curbme.app.service.accessibility.GuardianAccessibilityService
import rikka.shizuku.Shizuku

/**
 * Manages Shizuku permission checks, self-healing Accessibility Service,
 * granting WRITE_SECURE_SETTINGS, and promoting CurbMe to Device Owner.
 */
object ShizukuManager {
    private const val TAG = "ShizukuManager"
    private var isListenerRegistered = false

    /** Returns true if Shizuku app is installed on the device. */
    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Returns true if Shizuku app service is running on the device. */
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    /** Returns true if CurbMe has been granted Shizuku permission. */
    fun hasShizukuPermission(): Boolean {
        return try {
            if (!isShizukuAvailable()) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /** Registers listeners for Shizuku binder lifecycle and permission callbacks. */
    fun initListeners(onStateChanged: () -> Unit) {
        if (isListenerRegistered) return
        try {
            Shizuku.addBinderReceivedListenerSticky {
                onStateChanged()
            }
            Shizuku.addBinderDeadListener {
                onStateChanged()
            }
            Shizuku.addRequestPermissionResultListener { _, _ ->
                onStateChanged()
            }
            isListenerRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Shizuku listeners", e)
        }
    }

    /** Requests Shizuku permission dialog. */
    fun requestPermission(requestCode: Int = 1001) {
        try {
            if (isShizukuAvailable()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting Shizuku permission", e)
        }
    }

    /** Re-enables GuardianAccessibilityService automatically via Shizuku. Returns true if healed. */
    fun healAccessibilityService(context: Context): Boolean {
        if (!hasShizukuPermission()) return false

        val fullComponent = ComponentName(context, GuardianAccessibilityService::class.java).flattenToString()
        val shortComponent = ComponentName(context, GuardianAccessibilityService::class.java).flattenToShortString()

        val rawCurrent = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val currentList = rawCurrent.split(":")
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "null" }
            .toMutableList()

        if (!currentList.contains(fullComponent) && !currentList.contains(shortComponent)) {
            currentList.add(fullComponent)
        }

        val updatedServices = currentList.joinToString(":")

        // Clear disabled_accessibility_services and force AccessibilityManagerService to reload by cycling accessibility_enabled
        val cmd = "settings put secure disabled_accessibility_services ''; " +
                  "settings put secure enabled_accessibility_services '$updatedServices'; " +
                  "settings put secure accessibility_enabled 0; " +
                  "settings put secure accessibility_enabled 1; " +
                  "settings put secure accessibility_button_targets '$fullComponent'"

        ShizukuRunner.executeCommand(cmd)

        try { Thread.sleep(200L) } catch (_: Exception) {}

        return PermissionHelper.isAccessibilityEnabled(context)
    }

    /** Grants WRITE_SECURE_SETTINGS to CurbMe using Shizuku. */
    fun grantSecureSettings(context: Context, onResult: ((Boolean) -> Unit)? = null) {
        if (!hasShizukuPermission()) {
            onResult?.invoke(false)
            return
        }

        val pkg = context.packageName
        val cmd = "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"

        ShizukuRunner.executeCommand(cmd, object : ShizukuRunner.CommandListener {
            override fun onSuccess(output: String) {
                onResult?.invoke(true)
            }

            override fun onError(error: String) {
                onResult?.invoke(false)
            }
        })
    }

    /** Promotes CurbMe to Device Owner with 1-tap using Shizuku. */
    fun setDeviceOwner(context: Context, onResult: (Boolean, String) -> Unit) {
        if (!hasShizukuPermission()) {
            onResult(false, "Shizuku permission not granted")
            return
        }

        val adminComponent = ComponentName(context, CurbMeDeviceAdminReceiver::class.java).flattenToString()
        val cmd = "dpm set-device-owner '$adminComponent'"

        ShizukuRunner.executeCommand(cmd, object : ShizukuRunner.CommandListener {
            override fun onSuccess(output: String) {
                onResult(true, output)
            }

            override fun onError(error: String) {
                onResult(false, error)
            }
        })
    }

    /** Prevents CurbMe from being put to sleep by Doze or battery savers. */
    fun reinforceBackgroundExecution(context: Context) {
        if (!hasShizukuPermission()) return
        val pkg = context.packageName
        val cmd = "dumpsys deviceidle whitelist +$pkg; cmd appops set $pkg RUN_IN_BACKGROUND allow; cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow"
        ShizukuRunner.executeCommand(cmd)
    }
}
