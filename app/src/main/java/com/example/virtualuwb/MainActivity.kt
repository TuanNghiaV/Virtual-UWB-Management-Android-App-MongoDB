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
 * Root composable that sets up MaterialTheme and wires the [MapViewModel]
 * to the [AppNavHost].
 */
@Composable
fun VirtualUwbApp() {
    MaterialTheme {
        val viewModel: MapViewModel = viewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        AppNavHost(
            uiState = uiState,
            viewModel = viewModel
        )
    }
}
