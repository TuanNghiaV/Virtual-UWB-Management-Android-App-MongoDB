package com.example.virtualuwb.data.repository

import com.example.virtualuwb.data.mapper.GeofenceEventMapper.toDomain
import com.example.virtualuwb.data.remote.dto.EvaluateGeofenceEventsParams
import com.example.virtualuwb.data.remote.dto.GeofenceEventDto
import com.example.virtualuwb.data.remote.dto.GetRecentGeofenceEventsParams
import com.example.virtualuwb.data.remote.supabase.SupabaseClientProvider
import com.example.virtualuwb.domain.model.GeofenceEvent
import com.example.virtualuwb.domain.repository.GeofenceEventRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc

class SupabaseGeofenceEventRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client
) : GeofenceEventRepository {

    override suspend fun evaluateForDevice(deviceId: String): Int {
        return client.postgrest.rpc(
            "evaluate_geofence_events_for_device",
            EvaluateGeofenceEventsParams(p_device_code = deviceId)
        ).decodeAs<Int>()
    }

    override suspend fun getRecentEvents(limit: Int): List<GeofenceEvent> {
        val dtoList = client.postgrest.rpc(
            "get_recent_geofence_events",
            GetRecentGeofenceEventsParams(p_limit = limit)
        ).decodeList<GeofenceEventDto>()

        return dtoList.map { it.toDomain() }
    }

    suspend fun countEvents(): Int {
        return client.postgrest.rpc("count_geofence_events").decodeAs<Int>()
    }

    suspend fun testConnection(): Result<Int> {
        return try {
            val events = getRecentEvents(10)
            Result.success(events.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
