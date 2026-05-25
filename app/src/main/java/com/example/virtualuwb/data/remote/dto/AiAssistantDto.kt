package com.example.virtualuwb.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AiAssistantRequestDto(
    val question: String,
    val context: AiAssistantContextDto
)

@Serializable
data class AiAssistantContextDto(
    val tags: List<TagContextDto>,
    val geofences: List<GeofenceContextDto>,
    val phonePosition: PhonePositionDto?,
    val phone: PhoneContextDto? = null,
    val selectedTagRoute: RouteContextDto? = null,
    val note: String? = null
)

@Serializable
data class RouteContextDto(
    val source: String,
    val distanceMeters: Int?,
    val duration: String?,
    val steps: List<RouteStepContextDto>
)

@Serializable
data class RouteStepContextDto(
    val instruction: String?,
    val distanceMeters: Int?,
    val duration: String? = null
)

@Serializable
data class TagContextDto(
    val id: String,
    val name: String,
    val deviceCode: String? = null,
    val latitude: Double,
    val longitude: Double,
    val currentZoneName: String? = null,
    val currentZoneType: String? = null,
    val safetyStatus: String? = null,
    val distanceFromPhoneMeters: Double? = null,
    val bearingFromPhoneDegrees: Double? = null,
    val relativeDirection: String? = null,
    val navigationHint: String? = null,
    val navigationHintText: String? = null,
    val guidanceType: String? = null,
    val insideKnownUwbArea: Boolean? = null,
    val zoneName: String? = null,
    val zoneType: String? = null,
    val distance: String? = null,
    val direction: String? = null
)

@Serializable
data class GeofenceContextDto(
    val name: String,
    val type: String
)

@Serializable
data class PhonePositionDto(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class PhoneContextDto(
    val latitude: Double,
    val longitude: Double,
    val locationSource: String? = null,
    val currentZoneName: String?,
    val currentZoneType: String,
    val safetyStatus: String,
    val insideKnownUwbArea: Boolean? = null
)

@Serializable
data class AiAssistantResponseDto(
    val answer: String? = null,
    val error: String? = null,
    val detail: String? = null
)
