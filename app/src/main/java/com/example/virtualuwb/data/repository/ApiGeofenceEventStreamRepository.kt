package com.example.virtualuwb.data.repository

import android.util.Log
import com.example.virtualuwb.BuildConfig
import com.example.virtualuwb.data.remote.MongoClientProvider
import com.example.virtualuwb.data.remote.dto.MongoGeofenceEventDto
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.GeofenceEvent
import com.example.virtualuwb.domain.model.GeofenceEventType
import com.example.virtualuwb.domain.model.GeofenceType
import io.ktor.client.call.body
import io.ktor.client.request.prepareGet
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class ApiGeofenceEventStreamRepository {

    private val baseUrl = BuildConfig.MONGODB_API_BASE_URL
    private val json = Json { ignoreUnknownKeys = true }

    fun geofenceEventsFlow(): Flow<GeofenceEvent> = flow {
        var retryDelay = 2000L
        while (true) {
            try {
                Log.d("ApiEventStreamRepo", "Connecting to SSE stream: $baseUrl/api/events/stream")
                MongoClientProvider.client.prepareGet("$baseUrl/api/events/stream").execute { response ->
                    Log.d("ApiEventStreamRepo", "SSE stream response status: ${response.status}")
                    val channel: ByteReadChannel = response.body()
                    var currentEventName: String? = null
                    
                    // Reset backoff delay on successful connection
                    retryDelay = 2000L

                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) {
                            continue
                        }
                        if (trimmed.startsWith(":")) {
                            // Ping or comment line, ignore
                            continue
                        }
                        if (trimmed.startsWith("event:")) {
                            currentEventName = trimmed.removePrefix("event:").trim()
                        } else if (trimmed.startsWith("data:")) {
                            val data = trimmed.removePrefix("data:").trim()
                            if (currentEventName == "geofence_event") {
                                try {
                                    val dto = json.decodeFromString<MongoGeofenceEventDto>(data)
                                    val domainEvent = GeofenceEvent(
                                        id = dto.id?.hashCode()?.toLong() ?: dto._id?.hashCode()?.toLong(),
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
                                    emit(domainEvent)
                                } catch (e: Exception) {
                                    Log.e("ApiEventStreamRepo", "Failed to parse geofence_event data: $data", e)
                                }
                            }
                            currentEventName = null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ApiEventStreamRepo", "SSE stream disconnected or failed to connect: ${e.message}", e)
            }
            
            // Retry with delay
            Log.d("ApiEventStreamRepo", "Reconnecting in ${retryDelay / 1000}s...")
            delay(retryDelay)
            // Exponential backoff up to 30s
            retryDelay = (retryDelay * 2).coerceAtMost(30000L)
        }
    }
}
