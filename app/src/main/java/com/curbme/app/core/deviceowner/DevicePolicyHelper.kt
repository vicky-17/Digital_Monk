package com.curbme.app.core.deviceowner

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi

object DevicePolicyHelper {
    private const val TAG = "DevicePolicyHelper"

    /**
     * Applies (or clears) the Private DNS configuration.
     * Tries DevicePolicyManager API first if app is Device Owner.
     * Otherwise falls back to Settings.Global if WRITE_SECURE_SETTINGS permission is granted.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun applyPrivateDns(context: Context, enabled: Boolean, hostname: String): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, com.curbme.app.receiver.CurbMeDeviceAdminReceiver::class.java)

        // Path 1: Device Owner API
        if (dpm != null && dpm.isDeviceOwnerApp(context.packageName)) {
            try {
                if (enabled && hostname.isNotBlank()) {
                    val result = dpm.setGlobalPrivateDnsModeSpecifiedHost(adminComponent, hostname)
                    if (result == DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR) {
                        Log.i(TAG, "Private DNS set via Device Owner: $hostname")
                        return true
                    }
                    Log.w(TAG, "Device Owner Private DNS failed (code=$result)")
                } else {
                    dpm.setGlobalPrivateDnsModeOpportunistic(adminComponent)
                    Log.i(TAG, "Private DNS reset via Device Owner")
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Device Owner Private DNS failed: ${e.message}")
            }
        }

        // Path 2: WRITE_SECURE_SETTINGS API
        val hasSecureSettings = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        if (hasSecureSettings) {
            return try {
                val cr = context.contentResolver
                if (enabled && hostname.isNotBlank()) {
                    Settings.Global.putString(cr, "private_dns_mode", "hostname")
                    Settings.Global.putString(cr, "private_dns_specifier", hostname)
                    Log.i(TAG, "Private DNS set via WRITE_SECURE_SETTINGS: $hostname")
                } else {
                    Settings.Global.putString(cr, "private_dns_mode", "opportunistic")
                    Log.i(TAG, "Private DNS reset via WRITE_SECURE_SETTINGS")
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "WRITE_SECURE_SETTINGS Private DNS failed: ${e.message}")
                false
            }
        }

        Log.w(TAG, "Cannot apply Private DNS: Neither Device Owner nor WRITE_SECURE_SETTINGS granted")
        return false
    }

    /**
     * Locks or unlocks the system's Private DNS setting from being touched by the
     * user in Settings, using the DISALLOW_CONFIG_PRIVATE_DNS user restriction.
     * Requires Device Owner status.
     */
    fun setPrivateDnsUserRestriction(context: Context, restrict: Boolean): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, com.curbme.app.receiver.CurbMeDeviceAdminReceiver::class.java)

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
     * rather than trusting cached SharedPreferences.
     *
     * @return Pair(isEnabledViaHostname, hostnameOrEmpty).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun getCurrentPrivateDnsState(context: Context): Pair<Boolean, String> {
        return try {
            val cr = context.contentResolver
            val mode = Settings.Global.getString(cr, "private_dns_mode") ?: ""
            val host = Settings.Global.getString(cr, "private_dns_specifier") ?: ""
            val isEnabled = mode == "hostname" || mode == "provider_hostname"
            isEnabled to (if (isEnabled) host else "")
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
        val adminComponent = ComponentName(context, com.curbme.app.receiver.CurbMeDeviceAdminReceiver::class.java)

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
     * Checks the actual system Private DNS state. If Settings Shield (Private DNS Lock)
     * is enabled or Private DNS is enabled in app, but system Private DNS is turned off,
     * set to another mode, or set to a wrong/empty host, this method automatically detects
     * the mismatch and forces it back to desiredHost immediately.
     */
    fun reapplyPolicyIfMismatched(context: Context) {
        val prefs = com.curbme.app.data.local.prefs.PrefsManager(context)

        // Enforce re-application when Settings Shield is ON (isPrivateDnsLocked)
        // or when Private DNS is explicitly enabled in our app
        if (!prefs.isPrivateDnsLocked && !prefs.isPrivateDnsEnabled) return

        try {
            val cr = context.contentResolver
            val currentMode = Settings.Global.getString(cr, "private_dns_mode") ?: ""
            val currentHost = Settings.Global.getString(cr, "private_dns_specifier") ?: ""
            var desiredHost = prefs.selectedPrivateDnsHostname
            if (desiredHost.isBlank()) {
                desiredHost = "dns.adguard.com"
                prefs.selectedPrivateDnsHostname = desiredHost
            }

            // Auto-detection logic:
            // Check if private DNS is turned off, opportunistic, not in hostname/provider_hostname mode, or host is different/blank
            val isWrongMode = currentMode.isBlank() || currentMode == "off" || currentMode == "opportunistic" || (currentMode != "hostname" && currentMode != "provider_hostname")
            val isWrongHost = currentHost != desiredHost

            if (isWrongMode || isWrongHost) {
                Log.w(TAG, "DNS mismatch detected! Mode=$currentMode, Host=$currentHost. Re-applying $desiredHost")
                val success = applyPrivateDns(context, true, desiredHost)
                if (success) {
                    prefs.isPrivateDnsEnabled = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to re-apply DNS policy: ${e.message}", e)
        }
    }

    /**
     * Prevents or allows uninstallation of a specific package.
     * Requires Device Owner status.
     */
    fun setUninstallBlocked(context: Context, packageName: String, blocked: Boolean): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, com.curbme.app.receiver.CurbMeDeviceAdminReceiver::class.java)

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
        val adminComponent = ComponentName(context, com.curbme.app.receiver.CurbMeDeviceAdminReceiver::class.java)

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
        val adminComponent = ComponentName(context, com.curbme.app.receiver.CurbMeDeviceAdminReceiver::class.java)

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
        val adminComponent = ComponentName(context, com.curbme.app.receiver.CurbMeDeviceAdminReceiver::class.java)

        if (!dpm.isDeviceOwnerApp(context.packageName)) return emptyList()

        return try {
            dpm.getUserControlDisabledPackages(adminComponent)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
