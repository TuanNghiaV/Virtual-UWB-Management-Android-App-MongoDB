package com.example.virtualuwb.presentation.viewmodel

sealed class StartupUiState {
    object Loading : StartupUiState()
    object Ready : StartupUiState()
    data class Error(
        val message: String,
        val technicalMessage: String? = null
    ) : StartupUiState()
}
