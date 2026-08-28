package com.digitalmonk.app.ui.locks

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalmonk.app.data.local.db.dao.AppBlockDao
import com.digitalmonk.app.data.local.db.entity.AppBlockRule
import com.digitalmonk.app.data.local.prefs.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val isBlocked: Boolean = false
)

// Represents the state of our 6-step wizard
data class WizardState(
    val currentStep: Int = 1,
    val planType: String = "STAY_FOCUSED",
    val selectedPackages: Set<String> = emptySet(),
    val blockMethod: String = "INTERSTITIAL",
    val timingMode: String = "ON_DEMAND",
    val selectedDays: Set<Int> = emptySet(), // 1 for Monday, 7 for Sunday
    val planName: String = "",
    // Step 1 Details
    val allowedMinutes: Int = 0,
    val intervalMinutes: Int = 60,
    // Habit Details
    val maxLaunches: Int = 0,
    val maxTimePerLaunch: Int = 0,
    // Step 5 Details
    val stopChallenge: String = "NONE",
    val isAntiUninstall: Boolean = false
)

class LocksViewModel(
    private val context: Context,
    private val prefs: PrefsManager,
    private val appBlockDao: AppBlockDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isAppsLoading = MutableStateFlow(true)
    val isAppsLoading = _isAppsLoading.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())

    // Active rules from Database
    val activeRules = appBlockDao.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered apps for selection dialog
    val selectableApps = combine(_installedApps, _searchQuery) { apps, query ->
        apps.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
        }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Wizard State
    private val _wizardState = MutableStateFlow(WizardState())
    val wizardState = _wizardState.asStateFlow()

    // Website states (Keep for now)
    private val _blockedWebsites = MutableStateFlow(prefs.blockedWebsites)
    val blockedWebsites = _blockedWebsites.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isAppsLoading.value = true
            val startTime = System.currentTimeMillis()
            
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val appItems = packages.mapNotNull { appInfo ->
                if (appInfo.packageName == context.packageName) return@mapNotNull null
                val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent == null) return@mapNotNull null

                AppItem(
                    name = appInfo.loadLabel(pm).toString(),
                    packageName = appInfo.packageName,
                    icon = appInfo.loadIcon(pm),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            _installedApps.value = appItems
            
            // Ensure at least 2 seconds of loading time for UX
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime < 2000) {
                kotlinx.coroutines.delay(2000 - elapsedTime)
            }
            _isAppsLoading.value = false
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    // --- Wizard Actions ---

    fun nextStep() {
        _wizardState.update { it.copy(currentStep = it.currentStep + 1) }
    }

    fun previousStep() {
        if (_wizardState.value.currentStep > 1) {
            _wizardState.update { it.copy(currentStep = it.currentStep - 1) }
        }
    }

    fun updateWizard(update: (WizardState) -> WizardState) {
        _wizardState.update(update)
    }

    fun toggleAppSelection(packageName: String) {
        _wizardState.update { state ->
            val current = state.selectedPackages.toMutableSet()
            if (current.contains(packageName)) current.remove(packageName) else current.add(packageName)
            state.copy(selectedPackages = current)
        }
    }

    fun resetWizard() {
        _wizardState.value = WizardState()
    }

    fun clearCurrentStep() {
        _wizardState.update { state ->
            when (state.currentStep) {
                1 -> state.copy(planType = "STAY_FOCUSED")
                2 -> state.copy(selectedPackages = emptySet(), blockMethod = "SUSPEND")
                3 -> state.copy(timingMode = "ON_DEMAND", selectedDays = emptySet())
                4 -> state.copy(allowedMinutes = 0, maxLaunches = 0, intervalMinutes = 60)
                5 -> state.copy(stopChallenge = "NONE", isAntiUninstall = false)
                6 -> state.copy(planName = "")
                else -> state
            }
        }
    }

    fun toggleDaySelection(day: Int) {
        _wizardState.update { state ->
            val current = state.selectedDays.toMutableSet()
            if (current.contains(day)) current.remove(day) else current.add(day)
            state.copy(selectedDays = current)
        }
    }

    fun saveWizardPlan() {
        val state = _wizardState.value
        viewModelScope.launch(Dispatchers.IO) {
            val rules = state.selectedPackages.map { pkg ->
                val app = _installedApps.value.find { it.packageName == pkg }
                AppBlockRule(
                    packageName = pkg,
                    appName = app?.name ?: "Unknown App",
                    planType = state.planType,
                    allowedMinutes = state.allowedMinutes,
                    intervalMinutes = state.intervalMinutes,
                    maxLaunches = state.maxLaunches,
                    maxTimePerLaunch = state.maxTimePerLaunch,
                    blockMethod = state.blockMethod,
                    useInterstitial = state.blockMethod == "INTERSTITIAL",
                    timingMode = state.timingMode,
                    planName = state.planName,
                    stopChallengeType = state.stopChallenge,
                    isAntiUninstallEnabled = state.isAntiUninstall
                )
            }
            appBlockDao.insertRules(rules)
            // Reset wizard after saving
            _wizardState.value = WizardState()
        }
    }

    fun removeRule(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            appBlockDao.deleteRuleByPackage(packageName)
        }
    }

    // --- Website Actions ---

    fun addWebsite(domain: String) {
        val cleaned = domain.trim().lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
        if (cleaned.isNotEmpty()) {
            prefs.addBlockedWebsite(cleaned)
            _blockedWebsites.value = prefs.blockedWebsites
        }
    }

    fun removeWebsite(domain: String) {
        prefs.removeBlockedWebsite(domain)
        _blockedWebsites.value = prefs.blockedWebsites
    }
}