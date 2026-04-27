package com.example.digitalmonk.ui.auth

import com.example.digitalmonk.core.base.BaseViewModel
import com.example.digitalmonk.data.local.prefs.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AuthViewModel manages the state for the PIN gate.
 * It remains in Kotlin to support the Compose UI.
 */
class AuthViewModel(private val prefs: PrefsManager) : BaseViewModel() {

    private val _pinError = MutableStateFlow(false)
    val pinError: StateFlow<Boolean> = _pinError.asStateFlow()

    /**
     * Validates the entered PIN against the saved value.
     * Uses the centralized validation logic in the Java PrefsManager.
     */
    fun validatePin(entered: String): Boolean {
        // Calling the Java method we added earlier
        val isValid = prefs.validatePin(entered)
        _pinError.value = !isValid
        return isValid
    }

    fun clearError() {
        _pinError.value = false
    }
}