package com.example.virtualuwb.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.virtualuwb.data.repository.RepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class StartupViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<StartupUiState>(StartupUiState.Loading)
    val uiState: StateFlow<StartupUiState> = _uiState.asStateFlow()

    init {
        startStartupSequence()
    }

    fun startStartupSequence() {
        viewModelScope.launch {
            _uiState.value = StartupUiState.Loading
            try {
                // 1. Health check with timeout
                withTimeout(10_000L) {
                    val status = RepositoryProvider.apiHealthRepository.checkHealth()
                    if (status.status != "ok" || status.database != "connected") {
                        throw Exception("Backend status not OK or database disconnected (status=${status.status}, db=${status.database})")
                    }
                }

                // 2. Preload data with timeout
                withTimeout(15_000L) {
                    RepositoryProvider.apiUwbRepository.refreshDevices()
                    RepositoryProvider.apiGeofenceRepository.refreshGeofences()
                    // Preload recent events if possible
                    try {
                        RepositoryProvider.apiGeofenceEventRepository.getRecentEvents(10)
                    } catch (e: Exception) {
                        // Event loading failure is non-fatal for startup
                    }
                }

                _uiState.value = StartupUiState.Ready
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _uiState.value = StartupUiState.Error(
                    message = "Connection timeout. The VirtualUWB cloud is taking too long to respond.",
                    technicalMessage = "TimeoutException during startup sequence"
                )
            } catch (e: Exception) {
                _uiState.value = StartupUiState.Error(
                    message = "Cannot connect to VirtualUWB Cloud. Please check your internet connection or backend status.",
                    technicalMessage = e.message ?: e::class.simpleName
                )
            }
        }
    }

    fun skipToReady() {
        _uiState.value = StartupUiState.Ready
    }
}
