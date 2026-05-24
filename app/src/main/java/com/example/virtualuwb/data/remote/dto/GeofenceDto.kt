package com.example.virtualuwb.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GeofenceDto(
    val id: String? = null,
    val floor_id: String? = null,
    val geofence_code: String,
    val name: String,
    val type: String,
    val vertices: JsonElement,
    val is_active: Boolean = true,
    val created_at: String? = null,
    val updated_at: String? = null
)
