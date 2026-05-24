package com.example.virtualuwb.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpsertRectangleGeofenceParams(
    val p_geofence_code: String,
    val p_name: String,
    val p_type: String,
    val p_min_lat: Double,
    val p_max_lat: Double,
    val p_min_lon: Double,
    val p_max_lon: Double
)

@Serializable
data class SoftDeleteGeofenceParams(
    val p_geofence_code: String
)
