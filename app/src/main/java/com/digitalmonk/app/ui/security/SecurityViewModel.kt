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
                // Toggle never actually turned on — nothing to revert, just report it
                _privateDnsError.value =
                    "Could not enable Private DNS using \"$hostname\". " +
                            "This host doesn't support DNS-over-TLS, or the connection check failed. " +
                            "Pick a different host and try again."
            }
        }
    }

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

}