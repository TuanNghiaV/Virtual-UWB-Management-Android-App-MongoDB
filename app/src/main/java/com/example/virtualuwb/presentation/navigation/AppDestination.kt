package com.example.virtualuwb.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Map : AppDestination("map", "Map", Icons.Rounded.Map)
    data object Devices : AppDestination("devices", "Devices", Icons.Rounded.Devices)
    data object Geofences : AppDestination("geofences", "Geofences", Icons.Rounded.LocationOn)
    data object Events : AppDestination("events", "Events", Icons.Rounded.Notifications)
    data object FindMy : AppDestination("find_my", "Find", Icons.Rounded.Explore)
    data object Assistant : AppDestination("assistant", "AI", Icons.Rounded.AutoAwesome)
    data object Debug : AppDestination("debug", "System", Icons.Rounded.Settings)

    companion object {
        val items = listOf(Map, Devices, FindMy, Events, Assistant)
        val overflowItems = listOf(Geofences, Debug)
    }
}
