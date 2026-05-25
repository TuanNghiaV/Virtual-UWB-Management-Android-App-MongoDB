package com.example.virtualuwb.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MongoResponse<T>(
    val items: List<T>
)

@Serializable
data class MongoUwbDeviceDto(
    val id: String? = null,
    val _id: String? = null,
    val deviceCode: String,
    val name: String,
    val role: String,
    val latitude: Double,
    val longitude: Double,
    val isActive: Boolean = true,
    val floorId: String? = null,
    val currentZoneId: String? = null,
    val currentZoneName: String? = null,
    val currentZoneType: String? = null,
    val safetyStatus: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class MongoGeofenceDto(
    val id: String? = null,
    val _id: String? = null,
    val geofenceCode: String,
    val name: String,
    val type: String,
    val area: MongoGeoJsonPolygonDto,
    val isActive: Boolean = true,
    val floorId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class MongoGeoJsonPolygonDto(
    val type: String,
    val coordinates: List<List<List<Double>>>
)

@Serializable
data class MongoGeofenceEventDto(
    val id: String? = null,
    val _id: String? = null,
    val tagId: String,
    val tagName: String? = null,
    val tagCode: String? = null,
    val geofenceId: String,
    val geofenceName: String? = null,
    val geofenceType: String? = null,
    val eventType: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: String? = null
)

@Serializable
data class MongoUpdatePositionRequestDto(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class MongoUpdatePositionResponseDto(
    val device: MongoUwbDeviceDto,
    val zone: MongoZoneDto? = null,
    val events: List<MongoGeofenceEventDto> = emptyList()
)

@Serializable
data class MongoZoneDto(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val safetyStatus: String? = null
)

