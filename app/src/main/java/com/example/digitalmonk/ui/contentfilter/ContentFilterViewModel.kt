package com.example.digitalmonk.ui.contentfilter

import com.example.digitalmonk.core.base.BaseViewModel
import com.example.digitalmonk.data.local.prefs.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ContentFilterViewModel remains in Kotlin.
 * It manages the state for DNS/VPN filtering by interacting with the Java PrefsManager.
 */
class ContentFilterViewModel(private val prefs: PrefsManager) : BaseViewModel() {

    // ── UI State ──
    private val _isSafeSearchEnabled = MutableStateFlow(prefs.isSafeSearchEnabled)
    val isSafeSearchEnabled: StateFlow<Boolean> = _isSafeSearchEnabled.asStateFlow()

    private val _isYoutubeFilterEnabled = MutableStateFlow(prefs.isYoutubeFilterEnabled)
    val isYoutubeFilterEnabled: StateFlow<Boolean> = _isYoutubeFilterEnabled.asStateFlow()

    /**
     * Updates the SafeSearch setting in the Java PrefsManager.
     * In a full implementation, you might call a Java method here
     * to restart the DnsVpnService to apply changes immediately.
     */
    fun setSafeSearchEnabled(enabled: Boolean) {
        prefs.isSafeSearchEnabled = enabled
        _isSafeSearchEnabled.value = enabled
        // restartVpnService() // Optional logic to apply DNS changes immediately
    }

    /**
     * Updates the YouTube Restricted Mode setting in the Java PrefsManager.
     */
    fun setYoutubeFilterEnabled(enabled: Boolean) {
        prefs.isYoutubeFilterEnabled = enabled
        _isYoutubeFilterEnabled.value = enabled
    }

    /**
     * Refreshes the state from the Java source of truth.
     * Useful if the service changes settings in the background.
     */
    fun refreshSettings() {
        _isSafeSearchEnabled.value = prefs.isSafeSearchEnabled
        _isYoutubeFilterEnabled.value = prefs.isYoutubeFilterEnabled
    }
}