package com.example.virtualuwb.data.repository

import com.example.virtualuwb.data.mapper.GeofenceMapper.toDomain
import com.example.virtualuwb.data.mapper.GeofenceMapper.toRectangleRpcParams
import com.example.virtualuwb.data.remote.dto.GeofenceDto
import com.example.virtualuwb.data.remote.dto.SoftDeleteGeofenceParams
import com.example.virtualuwb.data.remote.supabase.SupabaseClientProvider
import com.example.virtualuwb.domain.model.Geofence
import com.example.virtualuwb.domain.repository.GeofenceRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SupabaseGeofenceRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client
) : GeofenceRepository {

    private val _geofencesFlow = MutableStateFlow<List<Geofence>>(emptyList())
    override val geofencesFlow: StateFlow<List<Geofence>> = _geofencesFlow.asStateFlow()

    override fun getCurrentGeofences(): List<Geofence> {
        return _geofencesFlow.value
    }

    override suspend fun refreshGeofences() {
        val dtoList = client.postgrest.rpc("get_active_geofences").decodeList<GeofenceDto>()
        
        val mapped = dtoList.mapNotNull {
            try {
                it.toDomain()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }.sortedBy { it.id }

        _geofencesFlow.value = mapped
    }

    override suspend fun addGeofence(geofence: Geofence) {
        client.postgrest.rpc("upsert_rectangle_geofence", geofence.toRectangleRpcParams())
        refreshGeofences()
    }

    override suspend fun updateGeofence(geofence: Geofence) {
        client.postgrest.rpc("upsert_rectangle_geofence", geofence.toRectangleRpcParams())
        refreshGeofences()
    }

    override suspend fun deleteGeofence(geofenceId: String) {
        client.postgrest.rpc(
            "soft_delete_geofence",
            SoftDeleteGeofenceParams(p_geofence_code = geofenceId)
        )
        refreshGeofences()
    }

    override suspend fun replaceAll(geofences: List<Geofence>) {
        geofences.forEach { geofence ->
            client.postgrest.rpc("upsert_rectangle_geofence", geofence.toRectangleRpcParams())
        }
        refreshGeofences()
    }

    override suspend fun resetToDefault() {
        refreshGeofences()
    }

    suspend fun testConnection(): Result<Int> {
        return try {
            refreshGeofences()
            Result.success(_geofencesFlow.value.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
