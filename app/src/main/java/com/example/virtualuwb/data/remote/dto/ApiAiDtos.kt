package com.example.virtualuwb.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiAiAssistantRequestDto(
    val message: String,
    val phone: ApiAiPhoneDto? = null,
    val selectedTagCode: String? = null,
    val route: ApiAiRouteDto? = null
)

@Serializable
data class ApiAiPhoneDto(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class ApiAiRouteDto(
    val distanceMeters: Int? = null,
    val duration: String? = null,
    val steps: List<ApiAiRouteStepDto> = emptyList()
)

@Serializable
data class ApiAiRouteStepDto(
    val instruction: String? = null,
    val distanceMeters: Int? = null,
    val duration: String? = null
)

@Serializable
data class ApiAiContextSummaryDto(
    val tags: Int? = null,
    val anchors: Int? = null,
    val geofences: Int? = null,
    val recentEvents: Int? = null,
    val selectedTagCode: String? = null
)

@Serializable
data class ApiAiAssistantResponseDto(
    val answer: String? = null,
    val contextSummary: ApiAiContextSummaryDto? = null,
    val source: String? = null,
    val error: String? = null,
    val message: String? = null,
    val detail: String? = null
)