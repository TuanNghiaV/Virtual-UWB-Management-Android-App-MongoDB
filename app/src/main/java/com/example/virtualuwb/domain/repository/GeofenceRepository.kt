package com.example.virtualuwb.domain.repository

import com.example.virtualuwb.domain.model.Geofence
import kotlinx.coroutines.flow.StateFlow

interface GeofenceRepository {
    val geofencesFlow: StateFlow<List<Geofence>>

    fun getCurrentGeofences(): List<Geofence>

    suspend fun refreshGeofences()

    suspend fun addGeofence(geofence: Geofence)

    suspend fun updateGeofence(geofence: Geofence)

    suspend fun deleteGeofence(geofenceId: String)

    suspend fun replaceAll(geofences: List<Geofence>)

    suspend fun resetToDefault()
}
