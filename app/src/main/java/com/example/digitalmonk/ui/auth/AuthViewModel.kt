package com.example.digitalmonk.ui.auth

import com.example.digitalmonk.core.base.BaseViewModel
import com.example.digitalmonk.data.local.prefs.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


class AuthViewModel(private val prefs: PrefsManager) : BaseViewModel() {

    private val _pinError = MutableStateFlow(false)
    val pinError: StateFlow<Boolean> = _pinError.asStateFlow()

    fun validatePin(entered: String): Boolean {
        val correct = entered == prefs.getPin()
        _pinError.value = !correct
        return correct
    }

    fun clearError() { _pinError.value = false }
}