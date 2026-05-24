package com.example.virtualuwb.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class GeofenceEventDto(
    val id: Long? = null,
    val device_code: String,
    val device_name: String,
    val geofence_code: String,
    val geofence_name: String,
    val geofence_type: String,
    val event_type: String,
    val latitude: Double,
    val longitude: Double,
    val created_at: String? = null
)

@Serializable
data class EvaluateGeofenceEventsParams(
    val p_device_code: String
)

@Serializable
data class GetRecentGeofenceEventsParams(
    val p_limit: Int = 50
)
