package com.digitalmonk.app.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.digitalmonk.app.core.utils.UsageStatsHelper
import com.digitalmonk.app.data.models.AppUsageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

data class DayUsageData(
    val label: String,
    val valueHours: Float,
    val date: Calendar
)

class UsageViewModel(application: Application) : AndroidViewModel(application) {
    private val usageStatsHelper = UsageStatsHelper(application)

    private val _usageStats = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val usageStats = _usageStats.asStateFlow()

    // Fixed "Today" stats specifically for the Dashboard
    private val _todayStats = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val todayStats = _todayStats.asStateFlow()

    private val _comparisonPercent = MutableStateFlow(0)
    val comparisonPercent = _comparisonPercent.asStateFlow()

    private val _isPermissionGranted = MutableStateFlow(usageStatsHelper.hasUsageStatsPermission())
    val isPermissionGranted = _isPermissionGranted.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _weeklyData = MutableStateFlow<List<DayUsageData>>(emptyList())
    val weeklyData = _weeklyData.asStateFlow()

    private val _selectedDayIndex = MutableStateFlow(calculateTodayIndex()) 
    val selectedDayIndex = _selectedDayIndex.asStateFlow()
    
    private val _weekOffset = MutableStateFlow(0)
    val weekOffset = _weekOffset.asStateFlow()

    init {
        refreshStats()
        startAutoRefresh()
    }

    fun refreshStats() {
        if (usageStatsHelper.hasUsageStatsPermission()) {
            _isPermissionGranted.value = true
            loadTodayOnly()

            // Update selected index if we are on the current week so the chart follows the day rollover
            if (_weekOffset.value == 0) {
                _selectedDayIndex.value = calculateTodayIndex()
            }

            loadWeekData()
        } else {
            _isPermissionGranted.value = false
        }
    }

    private fun loadTodayOnly() {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = usageStatsHelper.getTodayUsageStats()
            _todayStats.value = stats
            
            // Calculate comparison
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val yesterdayStats = usageStatsHelper.getUsageStatsForDay(yesterday)
            val todayTotal = stats.sumOf { it.usageTimeMs }
            val yesterdayTotal = yesterdayStats.sumOf { it.usageTimeMs }
            
            if (yesterdayTotal > 0) {
                val diff = todayTotal - yesterdayTotal
                val percent = (diff.toDouble() / yesterdayTotal * 100).toInt()
                _comparisonPercent.value = percent
            } else {
                _comparisonPercent.value = 0
            }
        }
    }

    fun selectDay(index: Int) {
        _selectedDayIndex.value = index
        loadSelectedDayStats()
    }

    fun changeWeek(offsetDelta: Int) {
        _weekOffset.value += offsetDelta
        loadWeekData()
    }

    private fun loadWeekData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val weekData = mutableListOf<DayUsageData>()
                val calendar = Calendar.getInstance()
                
                // Robust Monday calculation
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                }
                
                calendar.add(Calendar.WEEK_OF_YEAR, _weekOffset.value)
                
                val labels = listOf("M", "T", "W", "T", "F", "S", "S")
                
                for (i in 0..6) {
                    val date = calendar.clone() as Calendar
                    val stats = usageStatsHelper.getUsageStatsForDay(date)
                    val totalMs = stats.sumOf { it.usageTimeMs }
                    weekData.add(DayUsageData(labels[i], totalMs / 3600000f, date))
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                
                _weeklyData.value = weekData
                loadSelectedDayStats()
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    private fun loadSelectedDayStats() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val week = _weeklyData.value
                if (week.isNotEmpty() && _selectedDayIndex.value in week.indices) {
                    val selectedDate = week[_selectedDayIndex.value].date
                    val stats = usageStatsHelper.getUsageStatsForDay(selectedDate)
                    _usageStats.value = stats
                }
            } catch (_: Exception) {
                // Ignore
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun calculateTodayIndex(): Int {
        val calendar = Calendar.getInstance()
        // Mon=0, Tue=1, Wed=2, Thu=3, Fri=4, Sat=5, Sun=6
        val day = calendar.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
        return if (day == Calendar.SUNDAY) 6 else day - 2
    }

    private fun startAutoRefresh() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(300000) // Refresh every 5 minutes
                if (usageStatsHelper.hasUsageStatsPermission()) {
                    // refreshStats() updates todayStats, weekly labels, and handles day rollover
                    refreshStats()
                }
            }
        }
    }
}
