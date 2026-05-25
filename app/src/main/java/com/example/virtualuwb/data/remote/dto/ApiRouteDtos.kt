package com.example.virtualuwb.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiRoutePointDto(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class ApiRouteStepDto(
    val instruction: String? = null,
    val distanceMeters: Int? = null,
    val duration: String? = null
)

@Serializable
data class ApiRouteResponseDto(
    val distanceMeters: Int? = null,
    val duration: String? = null,
    val encodedPolyline: String? = null,
    val steps: List<ApiRouteStepDto> = emptyList(),
    val source: String? = null
)