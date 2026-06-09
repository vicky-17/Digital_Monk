package com.example.digitalmonk.ui.permissions

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitalmonk.core.utils.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import com.example.digitalmonk.receiver.MonkDeviceAdminReceiver

// Data class representing the live permission states of the device
data class PermissionsUiState(
    val isAccessibilityGranted: Boolean = false,
    val isDeviceAdminGranted: Boolean = false,
    val isUsageStatsGranted: Boolean = false,
    val isOverlayGranted: Boolean = false,
    val isAlwaysOnVpnGranted: Boolean = false,
    val isBatteryExempt: Boolean = false,
    val hasNotification: Boolean = false,
    // Note: SharedPreferences are used to track if a user visited un-queryable OEM screens
    val visitedAutostart: Boolean = false,
    val visitedMiuiBgPopup: Boolean = false,
    // One-shot event: signals the UI to launch the Device Admin system dialog
    val pendingDeviceAdminIntent: Intent? = null
)

class PermissionsViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    // Exposed Read-Only UI State stream
    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    init {
        checkAllPermissions()
    }

    /**
     * Queries the Android OS using your PermissionHelper utilities
     * and updates the UI state flow atomically.
     */
    fun checkAllPermissions() {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("monk_prefs", Context.MODE_PRIVATE)
            _uiState.update { current ->
                current.copy(
                    isAccessibilityGranted = PermissionHelper.isAccessibilityEnabled(context),
                    isDeviceAdminGranted   = PermissionHelper.isDeviceAdminActive(context),
                    isUsageStatsGranted    = PermissionHelper.hasUsageStatsPermission(context),
                    isOverlayGranted       = PermissionHelper.canDrawOverlays(context),
                    isAlwaysOnVpnGranted   = PermissionHelper.isAlwaysOnVpnActive(context),
                    isBatteryExempt        = PermissionHelper.isIgnoringBatteryOptimizations(context),
                    hasNotification        = PermissionHelper.hasNotificationPermission(context),
                    visitedAutostart       = prefs.getBoolean("visited_autostart", false),
                    visitedMiuiBgPopup     = prefs.getBoolean("visited_miui_bg_popup", false)
                )
            }
        }
    }

    /**
     * Device Admin requires ACTION_ADD_DEVICE_ADMIN which must be launched via
     * startActivityForResult() from an Activity — a ViewModel's application context
     * cannot show this system dialog directly even with FLAG_ACTIVITY_NEW_TASK.
     *
     * Fix: emit the Intent as a one-shot state event. PermissionsScreen observes it
     * and calls startActivity() from a proper Activity context via the launcher.
     * Call onDeviceAdminIntentHandled() after launching to clear the event.
     */
    fun buildDeviceAdminIntent(): Intent {
        val adminComponent = ComponentName(context, MonkDeviceAdminReceiver::class.java)
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Activate to prevent DigitalMonk from being uninstalled without parental permission."
            )
        }
    }

    /** Called by the UI after it has consumed and launched the device admin intent. */
    fun onDeviceAdminIntentHandled() {
        _uiState.update { it.copy(pendingDeviceAdminIntent = null) }
    }

    /**
     * Handles routing the user directly to the respective Android System Settings page.
     *
     * DEVICE_ADMIN is intentionally excluded here — it is handled via
     * pendingDeviceAdminIntent so the Activity can launch it with startActivityForResult.
     */
    fun triggerPermissionIntent(permissionType: String) {
        val prefs = context.getSharedPreferences("monk_prefs", Context.MODE_PRIVATE)

        when (permissionType) {
            "ACCESSIBILITY"        -> PermissionHelper.openAccessibilityServiceScreen(context)
            "USAGE_STATS"          -> PermissionHelper.openUsageAccessSettings(context)
            "OVERLAY"              -> PermissionHelper.openOverlaySettings(context)
            "ALWAYS_ON_VPN"        -> PermissionHelper.openVpnSettings(context)
            "BATTERY_OPTIMIZATION" -> PermissionHelper.openBatteryOptimizationSettings(context)
            "NOTIFICATIONS"        -> PermissionHelper.openAppNotificationSettings(context)

            // Device Admin: emit as a one-shot Intent event for the Activity to launch
            "DEVICE_ADMIN" -> {
                _uiState.update { it.copy(pendingDeviceAdminIntent = buildDeviceAdminIntent()) }
            }

            "AUTOSTART" -> {
                PermissionHelper.openXiaomiAutoStartSettings(context)
                checkAllPermissions()
            }
            "MIUI_POPUP" -> {
                PermissionHelper.openXiaomiBackgroundPopupSettings(context)
                checkAllPermissions()
            }
        }
    }
}
