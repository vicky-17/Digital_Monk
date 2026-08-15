package com.digitalmonk.app.ui.security

import androidx.lifecycle.ViewModel
import com.digitalmonk.app.data.local.prefs.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SecurityViewModel(private val prefsManager: PrefsManager) : ViewModel() {

    private val _isPermissionBlockEnabled = MutableStateFlow(prefsManager.isPermissionBlockEnabled)
    val isPermissionBlockEnabled: StateFlow<Boolean> = _isPermissionBlockEnabled.asStateFlow()

    private val _showConfirmDialog = MutableStateFlow(false)
    val showConfirmDialog: StateFlow<Boolean> = _showConfirmDialog.asStateFlow()

    private var pendingToggleState = true

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