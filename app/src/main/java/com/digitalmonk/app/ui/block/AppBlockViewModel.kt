package com.digitalmonk.app.ui.block

import androidx.lifecycle.viewModelScope
import com.digitalmonk.app.core.base.BaseViewModel
import com.digitalmonk.app.data.repository.UsageRepository
import com.digitalmonk.app.data.local.db.entity.UsageLogEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// This ViewModel now connects your Kotlin UI to your Java Data Layer
class AppBlockViewModel(private val repository: UsageRepository) : BaseViewModel() {

    private val _appList = MutableStateFlow<List<UsageLogEntity>>(emptyList())
    val appList: StateFlow<List<UsageLogEntity>> = _appList

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            // Calling your Java Repository method
            val logs = repository.allLogs
            _appList.value = logs ?: emptyList()
        }
    }

    fun toggleAppBlock(packageName: String, isBlocked: Boolean) {
        // Logic to update the block status in Java PrefsManager or Room DB
    }
}