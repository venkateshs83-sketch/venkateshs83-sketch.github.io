package com.venkat.tesladashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

const val API_KEY = "21eabdedb57260230cd563e42239096c5b96f29d96cb624a"

class DashboardViewModel : ViewModel() {
    var latestReading by mutableStateOf<Reading?>(null)
        private set
    var todaySummary by mutableStateOf<DailySummary?>(null)
        private set
    var history by mutableStateOf<List<DailySummary>>(emptyList())
        private set
    var monthHistory by mutableStateOf<List<DailySummary>>(emptyList())
        private set
    var weekSummary by mutableStateOf<PeriodSummary?>(null)
        private set
    var monthSummary by mutableStateOf<PeriodSummary?>(null)
        private set
    var chargingSessions by mutableStateOf<List<ChargingSession>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                latestReading = ApiClient.api.getStatus(API_KEY).latest
                todaySummary = ApiClient.api.getTodaySummary(API_KEY)
                history = ApiClient.api.getRangeSummary(API_KEY, 7)
                monthHistory = ApiClient.api.getMonthDays(API_KEY)
                weekSummary = ApiClient.api.getWeekSummary(API_KEY)
                monthSummary = ApiClient.api.getMonthSummary(API_KEY)
                chargingSessions = ApiClient.api.getChargingSessions(API_KEY, 30)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load data"
            } finally {
                isLoading = false
            }
        }
    }
}
