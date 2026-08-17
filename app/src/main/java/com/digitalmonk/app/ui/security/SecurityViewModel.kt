package com.digitalmonk.app.ui.security

import android.app.admin.DevicePolicyManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalmonk.app.data.local.prefs.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.digitalmonk.app.core.deviceowner.DevicePolicyHelper
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.Toast
import kotlinx.coroutines.withContext


class SecurityViewModel(
    private val prefsManager: PrefsManager,
    private val context: Context
) : ViewModel() {

    // ── Strict Permission Blocking State ──────────────────────────────────────
    private val _isPermissionBlockEnabled = MutableStateFlow(prefsManager.isPermissionBlockEnabled)
    val isPermissionBlockEnabled: StateFlow<Boolean> = _isPermissionBlockEnabled.asStateFlow()

    private val _showConfirmDialog = MutableStateFlow(false)
    val showConfirmDialog: StateFlow<Boolean> = _showConfirmDialog.asStateFlow()
    private var pendingToggleState = true

    // ── Private DNS States ────────────────────────────────────────────────────
    private val _isPrivateDnsEnabled = MutableStateFlow(prefsManager.isPrivateDnsEnabled)
    val isPrivateDnsEnabled: StateFlow<Boolean> = _isPrivateDnsEnabled.asStateFlow()

    private val _selectedHostname = MutableStateFlow(prefsManager.selectedPrivateDnsHostname)
    val selectedHostname: StateFlow<String> = _selectedHostname.asStateFlow()

    private val _hostnameList = MutableStateFlow(prefsManager.customPrivateDnsHostnames.toSet())
    val hostnameList: StateFlow<Set<String>> = _hostnameList.asStateFlow()

    private val _showAddHostnameDialog = MutableStateFlow(false)
    val showAddHostnameDialog: StateFlow<Boolean> = _showAddHostnameDialog.asStateFlow()

    private val _newHostnameInput = MutableStateFlow("")
    val newHostnameInput: StateFlow<String> = _newHostnameInput.asStateFlow()

    // Shown every time the user flips the toggle ON — forces an explicit host pick,
    // it is NEVER auto-populated from a previous session's applied host.
    private val _showEnableHostnameDialog = MutableStateFlow(false)
    val showEnableHostnameDialog: StateFlow<Boolean> = _showEnableHostnameDialog.asStateFlow()

    // Applying state — lets the UI show a spinner while the blocking DoT check runs
    private val _isApplyingPrivateDns = MutableStateFlow(false)
    val isApplyingPrivateDns: StateFlow<Boolean> = _isApplyingPrivateDns.asStateFlow()

    // Surfaces failures instead of swallowing them silently
    private val _privateDnsError = MutableStateFlow<String?>(null)
    val privateDnsError: StateFlow<String?> = _privateDnsError.asStateFlow()



    data class AppInfo(
        val packageName: String,
        val name: String,
        val icon: Drawable?,
        val isUninstallBlocked: Boolean,
        val isForceStopBlocked: Boolean
    )

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _showAppListDialog = MutableStateFlow(false)
    val showAppListDialog: StateFlow<Boolean> = _showAppListDialog.asStateFlow()

    // ── App Protection Confirmation ──────────────────────────────────────────
    data class AppConfirmData(
        val packageName: String,
        val type: AppProtectionType,
        val appName: String
    )
    enum class AppProtectionType { UNINSTALL, FORCE_STOP }

    private val _showAppConfirmDialog = MutableStateFlow<AppConfirmData?>(null)
    val showAppConfirmDialog: StateFlow<AppConfirmData?> = _showAppConfirmDialog.asStateFlow()

    fun clearPrivateDnsError() {
        _privateDnsError.value = null
    }
    /**
     * Called when the user flips the Private DNS switch.
     * - Turning ON never auto-applies a remembered host: it opens the picker dialog
     *   and only enables once the user explicitly confirms a host.
     * - Turning OFF disables immediately, no prompt required.
     */
    fun onPrivateDnsToggleRequested(enabledRequested: Boolean) {
        if (enabledRequested) {
            _showEnableHostnameDialog.value = true
        } else {
            disablePrivateDns()
        }
    }

    /** Immediately switches Private DNS to opportunistic (i.e. off / system default). */
    private fun disablePrivateDns() {
        prefsManager.isPrivateDnsEnabled = false
        _isPrivateDnsEnabled.value = false
        _privateDnsError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            _isApplyingPrivateDns.value = true
            DevicePolicyHelper.applyPrivateDns(context, false, "")
            _isApplyingPrivateDns.value = false
        }
    }

    /** User picked a host in the enable dialog and confirmed it. */
    fun confirmEnablePrivateDns(hostname: String) {
        _showEnableHostnameDialog.value = false
        _privateDnsError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            _isApplyingPrivateDns.value = true
            val success = DevicePolicyHelper.applyPrivateDns(context, true, hostname)
            _isApplyingPrivateDns.value = false

            if (success) {
                prefsManager.isPrivateDnsEnabled = true
                prefsManager.selectedPrivateDnsHostname = hostname
                _isPrivateDnsEnabled.value = true
                _selectedHostname.value = hostname
            } else {
                _privateDnsError.value =
                    "Could not enable Private DNS using \"$hostname\". " +
                            "This host doesn't support DNS-over-TLS, or the connection check failed. " +
                            "Pick a different host and try again."
            }
        }
    }

    fun deleteHostname(hostname: String) {
        if (prefsManager.isDefaultPrivateDnsHost(hostname)) return
        prefsManager.removeCustomPrivateDnsHostname(hostname)
        _hostnameList.value = prefsManager.customPrivateDnsHostnames.toSet()
    }

    fun isDefaultHostname(hostname: String): Boolean =
        prefsManager.isDefaultPrivateDnsHost(hostname)

    /** User dismissed the enable dialog without picking a host — toggle stays off. */
    fun dismissEnableHostnameDialog() {
        _showEnableHostnameDialog.value = false
    }

    fun onAddHostnameClicked() {
        _showAddHostnameDialog.value = true
    }

    fun dismissAddHostnameDialog() {
        _showAddHostnameDialog.value = false
        _newHostnameInput.value = ""
    }

    fun updateNewHostnameInput(input: String) {
        _newHostnameInput.value = input
    }

    fun saveNewHostname() {
        val input = _newHostnameInput.value.trim()
        if (input.isNotEmpty()) {
            val currentList = prefsManager.customPrivateDnsHostnames.toMutableSet()
            currentList.add(input)
            prefsManager.customPrivateDnsHostnames = currentList
            _hostnameList.value = currentList.toSet()
        }
        dismissAddHostnameDialog()
    }
    // ── Device Owner Check ────────────────────────────────────────────────────
    val isDeviceOwner: Boolean
        get() {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            return dpm?.isDeviceOwnerApp(context.packageName) == true
        }

    // ── Methods for Permission Blocking ───────────────────────────────────────
    fun onToggleClicked(isChecked: Boolean) {
        pendingToggleState = isChecked
        _showConfirmDialog.value = true
    }

    fun confirmToggle() {
        prefsManager.isPermissionBlockEnabled = pendingToggleState
        _isPermissionBlockEnabled.value = pendingToggleState
        _showConfirmDialog.value = false
    }

    fun dismissDialog() {
        _showConfirmDialog.value = false
    }


    // ── Private DNS "lock settings" state ─────────────────────────────────────
    private val _isPrivateDnsLocked = MutableStateFlow(prefsManager.isPrivateDnsLocked)
    val isPrivateDnsLocked: StateFlow<Boolean> = _isPrivateDnsLocked.asStateFlow()

    init {
        refreshPrivateDnsState()
    }

    /**
     * Re-syncs Private DNS UI state from the actual system settings rather than
     * trusting the cached preference. Called every time this ViewModel is
     * created (i.e. every time the Security screen is opened) so the toggle
     * never shows a stale state.
     */
    fun refreshPrivateDnsState() {
        if (!isDeviceOwner) return
        viewModelScope.launch(Dispatchers.IO) {
            val (systemEnabled, systemHost) = DevicePolicyHelper.getCurrentPrivateDnsState(context)
            val lockActive = DevicePolicyHelper.isPrivateDnsSettingsLocked(context)

            _isPrivateDnsEnabled.value = systemEnabled
            prefsManager.isPrivateDnsEnabled = systemEnabled

            if (systemEnabled && systemHost.isNotBlank()) {
                _selectedHostname.value = systemHost
                prefsManager.selectedPrivateDnsHostname = systemHost
            }

            _isPrivateDnsLocked.value = lockActive
            prefsManager.isPrivateDnsLocked = lockActive
        }
    }

    /**
     * Called when the parent flips "Lock Private DNS in Settings".
     * Independent of enabling/disabling Private DNS itself — this only
     * controls whether the child can reach the Private DNS screen in
     * system Settings at all.
     */
    fun onPrivateDnsLockToggleRequested(lockRequested: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DevicePolicyHelper.setPrivateDnsUserRestriction(context, lockRequested)
            if (success) {
                _isPrivateDnsLocked.value = lockRequested
                prefsManager.isPrivateDnsLocked = lockRequested
            } else {
                _privateDnsError.value =
                    "Could not update the Private DNS lock. Make sure Digital Monk is set as Device Owner."
            }
        }
    }






    fun onOpenAppListRequested() {
        _showAppListDialog.value = true
        fetchInstalledApps()
    }

    fun dismissAppListDialog() {
        _showAppListDialog.value = false
    }

    private fun fetchInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            val pm = context.packageManager

            // Get currently protected packages from the system
            val protectedFromStop = DevicePolicyHelper.getControlDisabledPackages(context)

            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
                .map { app ->
                    AppInfo(
                        packageName = app.packageName,
                        name = app.loadLabel(pm).toString(),
                        icon = app.loadIcon(pm),
                        isUninstallBlocked = DevicePolicyHelper.isUninstallBlocked(context, app.packageName),
                        isForceStopBlocked = protectedFromStop.contains(app.packageName)
                    )
                }
                .sortedBy { it.name }
            _installedApps.value = apps
            _isLoadingApps.value = false
        }
    }

    fun toggleUninstallProtection(packageName: String, blocked: Boolean) {
        // 1. Check if settings are locked when trying to turn OFF
        if (!blocked && prefsManager.isSettingsLocked) {
            Toast.makeText(context, "Cannot disable protection while settings are locked.", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. If turning ON, show confirmation dialog
        if (blocked) {
            val app = _installedApps.value.find { it.packageName == packageName }
            _showAppConfirmDialog.value = AppConfirmData(packageName, AppProtectionType.UNINSTALL, app?.name ?: packageName)
            return
        }

        // 3. Otherwise (turning OFF and not locked), proceed
        executeToggleUninstall(packageName, false)
    }

    fun toggleForceStopProtection(packageName: String, blocked: Boolean) {
        if (!blocked && prefsManager.isSettingsLocked) {
            Toast.makeText(context, "Cannot disable protection while settings are locked.", Toast.LENGTH_SHORT).show()
            return
        }

        if (blocked) {
            val app = _installedApps.value.find { it.packageName == packageName }
            _showAppConfirmDialog.value = AppConfirmData(packageName, AppProtectionType.FORCE_STOP, app?.name ?: packageName)
            return
        }

        executeToggleForceStop(packageName, false)
    }

    // --- Confirmation & Execution Helpers ---

    fun confirmAppToggle() {
        val data = _showAppConfirmDialog.value ?: return
        _showAppConfirmDialog.value = null
        when (data.type) {
            AppProtectionType.UNINSTALL -> executeToggleUninstall(data.packageName, true)
            AppProtectionType.FORCE_STOP -> executeToggleForceStop(data.packageName, true)
        }
    }

    fun dismissAppConfirmDialog() {
        _showAppConfirmDialog.value = null
    }

    private fun executeToggleUninstall(packageName: String, blocked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = DevicePolicyHelper.setUninstallBlocked(context, packageName, blocked)
            withContext(Dispatchers.Main) {
                if (success) {
                    _installedApps.value = _installedApps.value.map {
                        if (it.packageName == packageName) it.copy(isUninstallBlocked = blocked) else it
                    }
                    Toast.makeText(context, "Protection ${if (blocked) "Enabled" else "Disabled"} for $packageName", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun executeToggleForceStop(packageName: String, blocked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = DevicePolicyHelper.getControlDisabledPackages(context).toMutableList()
            if (blocked) {
                if (!currentList.contains(packageName)) currentList.add(packageName)
            } else {
                currentList.remove(packageName)
            }

            val success = DevicePolicyHelper.setControlDisabledPackages(context, currentList)
            if (success) {
                _installedApps.value = _installedApps.value.map {
                    if (it.packageName == packageName) it.copy(isForceStopBlocked = blocked) else it
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Force Stop ${if (blocked) "Disabled" else "Enabled"} for $packageName", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


}