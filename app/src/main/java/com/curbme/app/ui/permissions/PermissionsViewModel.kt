package com.curbme.app.ui.permissions

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.curbme.app.core.utils.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import com.curbme.app.receiver.MonkDeviceAdminReceiver
import com.curbme.app.service.accessibility.AllowlistManager
import com.curbme.app.service.vpn.DnsVpnService
import com.curbme.app.data.local.prefs.PrefsManager
import com.curbme.app.core.utils.Constants

data class PermissionsUiState(
    val isAccessibilityGranted: Boolean = false,
    val isDeviceAdminGranted: Boolean = false,
    val isUsageStatsGranted: Boolean = false,
    val isOverlayGranted: Boolean = false,
    val isVpnPermissionGranted: Boolean = false,   // NEW — basic VpnService.prepare() consent
    val isAlwaysOnVpnGranted: Boolean = false,
    val isBatteryExempt: Boolean = false,
    val hasNotification: Boolean = false,
    val visitedAutostart: Boolean = false,
    val visitedMiuiBgPopup: Boolean = false,
    val pendingDeviceAdminIntent: Intent? = null,
    val pendingVpnPermissionIntent: Intent? = null  // NEW — one-shot event for VpnService.prepare()
)

class PermissionsViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    init {
        checkAllPermissions()
    }

    fun checkAllPermissions() {
        AllowlistManager.getInstance().revokeAll()
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("monk_prefs", Context.MODE_PRIVATE)
            _uiState.update { current ->
                current.copy(
                    isAccessibilityGranted = PermissionHelper.isAccessibilityEnabled(context),
                    isDeviceAdminGranted   = PermissionHelper.isDeviceAdminActive(context),
                    isUsageStatsGranted    = PermissionHelper.hasUsageStatsPermission(context),
                    isOverlayGranted       = PermissionHelper.canDrawOverlays(context),
                    isVpnPermissionGranted = PermissionHelper.isVpnPermissionGranted(context), // NEW
                    isAlwaysOnVpnGranted   = PermissionHelper.isAlwaysOnVpnActive(context),
                    isBatteryExempt        = PermissionHelper.isIgnoringBatteryOptimizations(context),
                    hasNotification        = PermissionHelper.hasNotificationPermission(context),
                    visitedAutostart       = prefs.getBoolean("visited_autostart", false),
                    visitedMiuiBgPopup     = prefs.getBoolean("visited_miui_bg_popup", false)
                )
            }
        }
    }

    fun buildDeviceAdminIntent(): Intent {
        val adminComponent = ComponentName(context, MonkDeviceAdminReceiver::class.java)
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Activate to prevent CurbMe from being uninstalled without parental permission."
            )
        }
    }

    fun onDeviceAdminIntentHandled() {
        _uiState.update { it.copy(pendingDeviceAdminIntent = null) }
    }

    /** Called by the UI after it has consumed and launched the VPN consent intent. */
    fun onVpnPermissionIntentHandled() {
        _uiState.update { it.copy(pendingVpnPermissionIntent = null) }
    }

    /**
     * Called by the UI after the VpnService.prepare() system dialog result comes back.
     * If granted, restart the VPN service if SafeSearch/filter was meant to be on.
     */
    fun onVpnPermissionResult(granted: Boolean) {
        if (granted) {
            val prefs = PrefsManager(context)
            if (prefs.isSafeSearchEnabled) {
                context.startService(Intent(context, DnsVpnService::class.java))
            }
        }
        checkAllPermissions()
    }

    fun triggerPermissionIntent(permissionType: String) {
        val allowlist = AllowlistManager.getInstance()
        val prefs = context.getSharedPreferences("monk_prefs", Context.MODE_PRIVATE)

        when (permissionType) {
            "ACCESSIBILITY" -> {
                allowlist.allow(Constants.ALLOW_ACCESSIBILITY)
                PermissionHelper.openAccessibilityServiceScreen(context)
            }
            "USAGE_STATS" -> {
                allowlist.allow(Constants.ALLOW_USAGE_STATS)
                PermissionHelper.openUsageAccessSettings(context)
            }
            "OVERLAY" -> {
                allowlist.allow(Constants.ALLOW_OVERLAY)
                PermissionHelper.openOverlaySettings(context)
            }
            "VPN_PERMISSION" -> {
                // VpnService.prepare() must be launched via ActivityResultLauncher —
                // emit it as a one-shot event, same pattern as Device Admin.
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent != null) {
                    _uiState.update { it.copy(pendingVpnPermissionIntent = vpnIntent) }
                } else {
                    // Already granted — just refresh state
                    onVpnPermissionResult(granted = true)
                }
            }
            "ALWAYS_ON_VPN" -> {
                allowlist.allow(Constants.ALLOW_VPN)
                PermissionHelper.openVpnSettings(context)
            }
            "BATTERY_OPTIMIZATION" -> {
                allowlist.allow(Constants.ALLOW_BATTERY)
                PermissionHelper.openBatteryOptimizationSettings(context)
            }
            "NOTIFICATIONS" -> {
                allowlist.allow(Constants.ALLOW_NOTIFICATIONS)
                PermissionHelper.openAppNotificationSettings(context)
            }
            "DEVICE_ADMIN" -> {
                _uiState.update { it.copy(pendingDeviceAdminIntent = buildDeviceAdminIntent()) }
            }
            "AUTOSTART" -> {
                allowlist.allow(Constants.ALLOW_AUTOSTART)
                PermissionHelper.openXiaomiAutoStartSettings(context)
                checkAllPermissions()
            }
            "MIUI_POPUP" -> {
                allowlist.allow(Constants.ALLOW_OVERLAY)
                PermissionHelper.openXiaomiBackgroundPopupSettings(context)
                checkAllPermissions()
            }
        }
    }
}