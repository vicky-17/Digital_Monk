package com.curbme.app.service.monitor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hub & Spoke Architecture: The "Hub".
 * This singleton holds the current foreground app state in memory.
 * It uses StateFlow to broadcast changes instantly to any subscriber (Enforcers, Notification UI, etc.)
 */
object MonitorState {

    data class ForegroundAppInfo(
        val packageName: String?,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _foregroundApp = MutableStateFlow(ForegroundAppInfo(null))
    val foregroundApp: StateFlow<ForegroundAppInfo> = _foregroundApp.asStateFlow()

    /**
     * Updates the global foreground state.
     * Called by the "The Eye" (AppBlockEngineService) or Accessibility.
     */
    fun updateForegroundApp(packageName: String?) {
        if (_foregroundApp.value.packageName == packageName) return
        _foregroundApp.value = ForegroundAppInfo(packageName)
    }
}
