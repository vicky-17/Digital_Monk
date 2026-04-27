package com.example.digitalmonk.ui.dashboard

import android.app.Application
import com.example.digitalmonk.core.base.BaseViewModel
import com.example.digitalmonk.core.utils.PermissionHelper
import com.example.digitalmonk.data.local.prefs.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DashboardViewModel remains in Kotlin.
 * It polls the Java PermissionHelper to update the UI status of background services.
 */
class DashboardViewModel(
    private val application: Application,
    private val prefs: PrefsManager
) : BaseViewModel() {

    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    private val _isAccessibilityActive = MutableStateFlow(false)
    val isAccessibilityActive: StateFlow<Boolean> = _isAccessibilityActive.asStateFlow()

    init {
        refreshStatus()
    }

    /**
     * Polls the Java utility classes to check current system state.
     * Call this from the Fragment/Activity's onResume to ensure the Dashboard is accurate.
     */
    fun refreshStatus() {
        // 1. Check VPN status via Java PermissionHelper
        _isVpnActive.value = PermissionHelper.isVpnPermissionGranted(application)

        // 2. Check Accessibility status via Java PermissionHelper
        _isAccessibilityActive.value = PermissionHelper.isAccessibilityEnabled(application)
    }

    /**
     * Checks if the parent has enabled the "Keep Alive" watchdog in Java Prefs.
     */
    fun isWatchdogEnabled(): Boolean {
        return prefs.isKeepVpnAlive
    }
}