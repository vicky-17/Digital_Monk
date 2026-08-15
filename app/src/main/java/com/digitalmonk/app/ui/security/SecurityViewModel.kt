package com.digitalmonk.app.ui.security

import android.app.admin.DevicePolicyManager
import android.content.Context
import androidx.lifecycle.ViewModel
import com.digitalmonk.app.data.local.prefs.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // ── Methods for Private DNS ───────────────────────────────────────────────
    fun togglePrivateDns(enabled: Boolean) {
        prefsManager.isPrivateDnsEnabled = enabled
        _isPrivateDnsEnabled.value = enabled

        // Apply changes via backend policy helper
        DevicePolicyHelper.applyPrivateDns(context, enabled, _selectedHostname.value)
    }

    fun selectHostname(hostname: String) {
        prefsManager.selectedPrivateDnsHostname = hostname
        _selectedHostname.value = hostname

        // If Private DNS is currently enabled, re-apply with the newly selected hostname immediately
        if (_isPrivateDnsEnabled.value) {
            DevicePolicyHelper.applyPrivateDns(context, true, hostname)
        }
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

            // Automatically select it
            selectHostname(input)
        }
        dismissAddHostnameDialog()
    }
}