package com.example.virtualuwb.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RoutePoint(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class RouteStep(
    val distanceMeters: Int? = null,
    val duration: String? = null,
    val instruction: String? = null,
    val encodedPolyline: String? = null
)

@Serializable
data class RouteResult(
    val success: Boolean,
    val distanceMeters: Int? = null,
    val duration: String? = null,
    val encodedPolyline: String? = null,
    val steps: List<RouteStep> = emptyList(),
    val error: String? = null,
    val source: String
)
