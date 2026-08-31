package com.curbme.app.core.deviceowner

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

object DevicePolicyHelper {
    private const val TAG = "DevicePolicyHelper"

    /**
     * Applies (or clears) the Private DNS configuration using the sanctioned
     * DevicePolicyManager APIs for device owners. Settings.Global.putString()
     * requires WRITE_SECURE_SETTINGS, which device owner status alone does NOT
     * grant — that was the root cause of the previous silent failure.
     *
     * NOTE: setGlobalPrivateDnsModeSpecifiedHost() performs a blocking network
     * connectivity check (RFC 7858 DoT handshake) to validate the host, so this
     * MUST be called off the main thread. Callers are responsible for that.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun applyPrivateDns(context: Context, enabled: Boolean, hostname: String): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, com.curbme.app.receiver.MonkDeviceAdminReceiver::class.java)

        if (dpm == null || !dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "Cannot apply Private DNS: App is not Device Owner")
            return false
        }

        return try {
            if (enabled && hostname.isNotBlank()) {
                when (val result = dpm.setGlobalPrivateDnsModeSpecifiedHost(adminComponent, hostname)) {
                    DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR -> {
                        Log.i(TAG, "Private DNS set to hostname mode: $hostname")
                        true
                    }
                    DevicePolicyManager.PRIVATE_DNS_SET_ERROR_HOST_NOT_SERVING -> {
                        Log.w(TAG, "Host $hostname does not support DNS-over-TLS")
                        false
                    }
                    DevicePolicyManager.PRIVATE_DNS_SET_ERROR_FAILURE_SETTING -> {
                        Log.w(TAG, "General failure setting Private DNS (code=$result)")
                        false
                    }
                    else -> false
                }
            } else {
                dpm.setGlobalPrivateDnsModeOpportunistic(adminComponent)
                Log.i(TAG, "Private DNS reset to opportunistic mode")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply Private DNS configuration: ${e.message}")
            false
        }
    }

    /**
     * Locks or unlocks the system's Private DNS setting from being touched by the
     * user in Settings, using the DISALLOW_CONFIG_PRIVATE_DNS user restriction.
     * Requires Device Owner status.
     */
    fun setPrivateDnsUserRestriction(context: Context, restrict: Boolean): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, com.curbme.app.receiver.MonkDeviceAdminReceiver::class.java)

        if (dpm == null || !dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "Cannot set Private DNS restriction: App is not Device Owner")
            return false
        }

        return try {
            if (restrict) {
                dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
                Log.i(TAG, "Private DNS settings locked from user")
            } else {
                dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
                Log.i(TAG, "Private DNS settings unlocked for user")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set Private DNS user restriction: ${e.message}")
            false
        }
    }

    /**
     * Reads the actual current Private DNS state directly from the system,
     * rather than trusting cached SharedPreferences. Used to re-sync UI state
     * every time the Security screen is opened, since the mode can change
     * outside the app.
     *
     * @return Pair(isEnabledViaHostname, hostnameOrEmpty).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun getCurrentPrivateDnsState(context: Context): Pair<Boolean, String> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, com.curbme.app.receiver.MonkDeviceAdminReceiver::class.java)

        if (dpm == null || !dpm.isDeviceOwnerApp(context.packageName)) {
            return false to ""
        }

        return try {
            val mode = dpm.getGlobalPrivateDnsMode(adminComponent)
            if (mode == DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME) {
                val host = dpm.getGlobalPrivateDnsHost(adminComponent) ?: ""
                true to host
            } else {
                false to ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read current Private DNS state: ${e.message}")
            false to ""
        }
    }

    /**
     * Returns true if DISALLOW_CONFIG_PRIVATE_DNS is currently active, i.e. the
     * Private DNS entry in system Settings is locked from the user.
     */
    fun isPrivateDnsSettingsLocked(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, com.curbme.app.receiver.MonkDeviceAdminReceiver::class.java)

        if (dpm == null || !dpm.isDeviceOwnerApp(context.packageName)) {
            return false
        }

        return try {
            val restrictions = dpm.getUserRestrictions(adminComponent)
            restrictions.getBoolean(android.os.UserManager.DISALLOW_CONFIG_PRIVATE_DNS, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read Private DNS restriction state: ${e.message}")
            false
        }
    }

    /**
     * Checks the actual system Private DNS state. If the "Lock" is on in Prefs
     * but the system is NOT set to the correct hostname, this method forces
     * it back immediately.
     */
    fun reapplyPolicyIfMismatched(context: Context) {
        val prefs = com.curbme.app.data.local.prefs.PrefsManager(context)

        // Only enforce if both Enabled and Locked are true in our app
        if (!prefs.isPrivateDnsEnabled || !prefs.isPrivateDnsLocked) return

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
        val adminComponent = android.content.ComponentName(context, com.curbme.app.receiver.MonkDeviceAdminReceiver::class.java)

        if (dpm == null || !dpm.isDeviceOwnerApp(context.packageName)) return

        try {
            val currentMode = dpm.getGlobalPrivateDnsMode(adminComponent)
            val currentHost = dpm.getGlobalPrivateDnsHost(adminComponent) ?: ""
            val desiredHost = prefs.selectedPrivateDnsHostname

            val isWrongMode = currentMode != android.app.admin.DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME
            val isWrongHost = currentHost != desiredHost

            if (isWrongMode || isWrongHost) {
                android.util.Log.w("DevicePolicyHelper", "DNS mismatch detected! Mode=$currentMode, Host=$currentHost. Re-applying $desiredHost")

                // Force the policy back
                dpm.setGlobalPrivateDnsModeSpecifiedHost(adminComponent, desiredHost)

                // Also ensure the standard user restriction is still set
                dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            }
        } catch (e: Exception) {
            android.util.Log.e("DevicePolicyHelper", "Failed to re-apply DNS policy: ${e.message}")
        }
    }


    /**
     * Prevents or allows uninstallation of a specific package.
     * Requires Device Owner status.
     */
    fun setUninstallBlocked(context: Context, packageName: String, blocked: Boolean): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, com.curbme.app.receiver.MonkDeviceAdminReceiver::class.java)

        if (dpm == null || !dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "Cannot set uninstall block: App is not Device Owner")
            return false
        }

        return try {
            dpm.setUninstallBlocked(adminComponent, packageName, blocked)
            Log.i(TAG, "Uninstall blocked for $packageName: $blocked")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set uninstall block for $packageName: ${e.message}")
            false
        }
    }

    /**
     * Checks if uninstallation is blocked for a specific package.
     */
    fun isUninstallBlocked(context: Context, packageName: String): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, com.curbme.app.receiver.MonkDeviceAdminReceiver::class.java)

        if (dpm == null || !dpm.isDeviceOwnerApp(context.packageName)) {
            return false
        }

        return try {
            dpm.isUninstallBlocked(adminComponent, packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check uninstall block for $packageName: ${e.message}")
            false
        }
    }


    /**
     * Prevents apps in the list from being disabled or force-stopped by the user.
     * Requires Device Owner.
     */
    fun setControlDisabledPackages(context: Context, packages: List<String>): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        val adminComponent = ComponentName(context, "com.curbme.app.receiver.MonkDeviceAdminReceiver")

        if (!dpm.isDeviceOwnerApp(context.packageName)) return false

        return try {
            dpm.setUserControlDisabledPackages(adminComponent, packages)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set control disabled packages", e)
            false
        }
    }

    /**
     * Returns the list of packages currently protected from being disabled/stopped.
     */
    fun getControlDisabledPackages(context: Context): List<String> {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return emptyList()
        val adminComponent = ComponentName(context, "com.curbme.app.receiver.MonkDeviceAdminReceiver")

        if (!dpm.isDeviceOwnerApp(context.packageName)) return emptyList()

        return try {
            dpm.getUserControlDisabledPackages(adminComponent)
        } catch (e: Exception) {
            emptyList()
        }
    }

}