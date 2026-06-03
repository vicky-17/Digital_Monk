package com.example.digitalmonk.ui.permissions

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitalmonk.core.utils.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.core.content.edit

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
    val visitedMiuiPower: Boolean = false,
    val visitedMiuiBgPopup: Boolean = false
)

class PermissionsViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    // Exposed Read-Only UI State stream
    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    init {
        // Run an immediate initialization check when the viewmodel is created
        checkAllPermissions()
    }

    /**
     * Queries the Android OS using your PermissionHelper utilities
     * and updates the UI state flow atomically.
     */
    fun checkAllPermissions() {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("monk_prefs", Context.MODE_PRIVATE)
            _uiState.update { currentState ->
                currentState.copy(
                    isAccessibilityGranted = PermissionHelper.isAccessibilityEnabled(context),
                    isDeviceAdminGranted = PermissionHelper.isDeviceAdminActive(context),
                    isUsageStatsGranted = PermissionHelper.hasUsageStatsPermission(context),
                    isOverlayGranted = PermissionHelper.canDrawOverlays(context),
                    isAlwaysOnVpnGranted = PermissionHelper.isAlwaysOnVpnActive(context),
                    isBatteryExempt = PermissionHelper.isIgnoringBatteryOptimizations(context),
                    hasNotification = PermissionHelper.hasNotificationPermission(context),
                    visitedAutostart = prefs.getBoolean("visited_autostart", false),
                    visitedMiuiPower = prefs.getBoolean("visited_miui_power", false),
                    visitedMiuiBgPopup = prefs.getBoolean("visited_miui_bg_popup", false)
                )
            }
        }
    }

    /**
     * Handles routing the user directly to the respective Android System Settings page
     * depending on which "Activate Permission" button they click.
     */
    fun triggerPermissionIntent(permissionType: String) {
        val prefs = context.getSharedPreferences("monk_prefs", Context.MODE_PRIVATE)

        when (permissionType) {
            "ACCESSIBILITY" -> PermissionHelper.openAccessibilityServiceScreen(context)
            "DEVICE_ADMIN" -> PermissionHelper.openDeviceAdminSettings(context)
            "USAGE_STATS" -> PermissionHelper.openUsageAccessSettings(context)
            "OVERLAY" -> PermissionHelper.openOverlaySettings(context)
            "ALWAYS_ON_VPN" -> PermissionHelper.openVpnSettings(context)
            "BATTERY_OPTIMIZATION" -> PermissionHelper.openBatteryOptimizationSettings(context)
            "NOTIFICATIONS" -> PermissionHelper.openAppNotificationSettings(context)
            "AUTOSTART" -> {
                prefs.edit { putBoolean("visited_autostart", true) }
                PermissionHelper.openXiaomiAutoStartSettings(context)
                checkAllPermissions() // Immediately force status refresh update
            }
            "MIUI_POWER" -> {
                prefs.edit { putBoolean("visited_miui_power", true) }
                // Reuse explicit package intents inside native settings activities if required
                checkAllPermissions()
            }
            "MIUI_POPUP" -> {
                prefs.edit { putBoolean("visited_miui_bg_popup", true) }
                checkAllPermissions()
            }
        }
    }
}