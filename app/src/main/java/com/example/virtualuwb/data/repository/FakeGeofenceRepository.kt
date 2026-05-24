package com.example.virtualuwb.data.repository

import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.Geofence
import com.example.virtualuwb.domain.model.GeofenceType
import com.example.virtualuwb.domain.repository.GeofenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeGeofenceRepository : GeofenceRepository {

    private val _geofencesFlow = MutableStateFlow(createDefaultGeofences())
    override val geofencesFlow: StateFlow<List<Geofence>> = _geofencesFlow.asStateFlow()

    override fun getCurrentGeofences(): List<Geofence> {
        return _geofencesFlow.value
    }

    override suspend fun refreshGeofences() {
        // No-op for local fake repository
    }

    override suspend fun addGeofence(geofence: Geofence) {
        val currentList = _geofencesFlow.value
        _geofencesFlow.value = if (currentList.any { it.id == geofence.id }) {
            currentList.map { if (it.id == geofence.id) geofence else it }
        } else {
            currentList + geofence
        }
    }

    override suspend fun updateGeofence(geofence: Geofence) {
        val currentList = _geofencesFlow.value
        _geofencesFlow.value = if (currentList.any { it.id == geofence.id }) {
            currentList.map { if (it.id == geofence.id) geofence else it }
        } else {
            currentList + geofence
        }
    }

    override suspend fun deleteGeofence(geofenceId: String) {
        _geofencesFlow.value = _geofencesFlow.value.filter { it.id != geofenceId }
    }

    override suspend fun replaceAll(geofences: List<Geofence>) {
        _geofencesFlow.value = geofences
    }

    override suspend fun resetToDefault() {
        _geofencesFlow.value = createDefaultGeofences()
    }

    companion object {
        fun createDefaultGeofences(): List<Geofence> = listOf(
            Geofence(
                id = "restricted-zone-1",
                name = "Restricted Zone",
                type = GeofenceType.RESTRICTED_ZONE,
                vertices = listOf(
                    GeoPoint(21.036700, 105.834800),
                    GeoPoint(21.036700, 105.835000),
                    GeoPoint(21.036500, 105.835000),
                    GeoPoint(21.036500, 105.834800)
                )
            ),
            Geofence(
                id = "safe-zone-1",
                name = "Safe Zone",
                type = GeofenceType.SAFE_ZONE,
                vertices = listOf(
                    GeoPoint(21.037100, 105.834300),
                    GeoPoint(21.037100, 105.834500),
                    GeoPoint(21.036900, 105.834500),
                    GeoPoint(21.036900, 105.834300)
                )
            )
        )
    }
}
