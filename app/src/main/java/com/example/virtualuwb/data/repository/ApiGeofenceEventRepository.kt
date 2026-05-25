package com.example.virtualuwb.data.repository

import android.util.Log
import com.example.virtualuwb.BuildConfig
import com.example.virtualuwb.data.remote.MongoClientProvider
import com.example.virtualuwb.data.remote.dto.MongoResponse
import com.example.virtualuwb.data.remote.dto.MongoGeofenceEventDto
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.GeofenceEvent
import com.example.virtualuwb.domain.model.GeofenceEventType
import com.example.virtualuwb.domain.model.GeofenceType
import com.example.virtualuwb.domain.repository.GeofenceEventRepository
import io.ktor.client.call.body
import io.ktor.client.request.get

class ApiGeofenceEventRepository : GeofenceEventRepository {

    private val baseUrl = BuildConfig.MONGODB_API_BASE_URL

    override suspend fun evaluateForDevice(deviceId: String): Int {
        Log.w("ApiGeofenceEventRepository", "evaluateForDevice not supported in read-only MongoDB API mode")
        return 0
    }

    override suspend fun getRecentEvents(limit: Int): List<GeofenceEvent> {
        try {
            val response: MongoResponse<MongoGeofenceEventDto> = MongoClientProvider.client
                .get("$baseUrl/api/events/recent?limit=$limit")
                .body()

            return response.items.map { dto ->
                GeofenceEvent(
                    id = null,
                    deviceId = dto.tagCode ?: dto.tagId,
                    deviceName = dto.tagName ?: "Unknown Tag",
                    geofenceId = dto.geofenceId,
                    geofenceName = dto.geofenceName ?: "Unknown Geofence",
                    geofenceType = runCatching { GeofenceType.valueOf(dto.geofenceType ?: "") }
                        .getOrDefault(GeofenceType.RESTRICTED_ZONE),
                    eventType = runCatching { GeofenceEventType.valueOf(dto.eventType) }
                        .getOrDefault(GeofenceEventType.ENTER),
                    position = GeoPoint(dto.latitude, dto.longitude),
                    createdAt = dto.createdAt
                )
            }
        } catch (e: Exception) {
            Log.e("ApiGeofenceEventRepository", "Failed to fetch recent events: ${e.message}", e)
            throw e
        }
    }
}
