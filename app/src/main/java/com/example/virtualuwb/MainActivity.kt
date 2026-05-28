package com.example.virtualuwb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.virtualuwb.presentation.screen.map.IndoorMapScreen
import com.example.virtualuwb.presentation.viewmodel.MapViewModel
import com.example.virtualuwb.presentation.navigation.AppNavHost

import com.example.virtualuwb.presentation.viewmodel.StartupViewModel
import com.example.virtualuwb.presentation.viewmodel.StartupUiState
import com.example.virtualuwb.presentation.screen.startup.StartupLoadingScreen
import com.example.virtualuwb.presentation.screen.startup.StartupErrorScreen

/**
 * Single-activity entry point for the VirtualUWB Indoor Positioning System.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VirtualUwbApp()
        }
    }
}

/**
 * Root composable that sets up MaterialTheme, observes startup state,
 * and gates access to [AppNavHost] until cloud data is ready or local mode is selected.
 */
@Composable
fun VirtualUwbApp() {
    MaterialTheme {
        val startupViewModel: StartupViewModel = viewModel()
        val startupState by startupViewModel.uiState.collectAsStateWithLifecycle()

        val viewModel: MapViewModel = viewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        when (val state = startupState) {
            is StartupUiState.Loading -> {
                StartupLoadingScreen()
            }
            is StartupUiState.Error -> {
                StartupErrorScreen(
                    message = state.message,
                    technicalMessage = state.technicalMessage,
                    onRetry = { startupViewModel.startStartupSequence() },
                    onContinueLocal = {
                        viewModel.switchToLocal()
                        startupViewModel.skipToReady()
                    }
                )
            }
            is StartupUiState.Ready -> {
                AppNavHost(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }
}
