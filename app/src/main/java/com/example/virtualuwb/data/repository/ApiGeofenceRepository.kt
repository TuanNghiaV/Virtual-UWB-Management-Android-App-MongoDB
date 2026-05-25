package com.example.virtualuwb.data.repository

import android.util.Log
import com.example.virtualuwb.BuildConfig
import com.example.virtualuwb.data.remote.MongoClientProvider
import com.example.virtualuwb.data.remote.dto.MongoResponse
import com.example.virtualuwb.data.remote.dto.MongoGeofenceDto
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.Geofence
import com.example.virtualuwb.domain.model.GeofenceType
import com.example.virtualuwb.domain.repository.GeofenceRepository
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ApiGeofenceRepository : GeofenceRepository {

    private val _geofencesFlow = MutableStateFlow<List<Geofence>>(emptyList())
    override val geofencesFlow: StateFlow<List<Geofence>> = _geofencesFlow.asStateFlow()

    private val baseUrl = BuildConfig.MONGODB_API_BASE_URL

    override suspend fun refreshGeofences() {
        try {
            val response: MongoResponse<MongoGeofenceDto> = MongoClientProvider.client
                .get("$baseUrl/api/geofences")
                .body()

            val mapped = response.items.mapNotNull { dto ->
                try {
                    val parsedType = runCatching { GeofenceType.valueOf(dto.type) }
                        .getOrDefault(GeofenceType.RESTRICTED_ZONE)

                    // GeoJSON coordinates is a 3D array: [[[lng, lat], [lng, lat], ...]]
                    // Take the first array (outer ring)
                    val parsedVertices = dto.area.coordinates.firstOrNull()?.map { point ->
                        GeoPoint(latitude = point[1], longitude = point[0])
                    }.orEmpty().toMutableList()

                    // Remove redundant closed polygon duplicate end point
                    if (parsedVertices.size > 1 && parsedVertices.first() == parsedVertices.last()) {
                        parsedVertices.removeAt(parsedVertices.size - 1)
                    }

                    if (parsedVertices.size < 3) {
                        null
                    } else {
                        Geofence(
                            id = dto.geofenceCode,
                            name = dto.name,
                            type = parsedType,
                            vertices = parsedVertices
                        )
                    }
                } catch (e: Exception) {
                    Log.e("ApiGeofenceRepository", "Error parsing geofence: ${dto.geofenceCode}", e)
                    null
                }
            }

            _geofencesFlow.value = mapped
        } catch (e: Exception) {
            Log.e("ApiGeofenceRepository", "Failed to refresh geofences from MongoDB API: ${e.message}", e)
            throw e
        }
    }

    override fun getCurrentGeofences(): List<Geofence> {
        return _geofencesFlow.value
    }

    override suspend fun addGeofence(geofence: Geofence) {
        Log.w("ApiGeofenceRepository", "addGeofence not supported in read-only MongoDB API mode")
    }

    override suspend fun updateGeofence(geofence: Geofence) {
        Log.w("ApiGeofenceRepository", "updateGeofence not supported in read-only MongoDB API mode")
    }

    override suspend fun deleteGeofence(geofenceId: String) {
        Log.w("ApiGeofenceRepository", "deleteGeofence not supported in read-only MongoDB API mode")
    }

    override suspend fun replaceAll(geofences: List<Geofence>) {
        Log.w("ApiGeofenceRepository", "replaceAll not supported in read-only MongoDB API mode")
    }

    override suspend fun resetToDefault() {
        refreshGeofences()
    }
}
