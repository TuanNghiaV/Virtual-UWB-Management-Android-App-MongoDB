package com.example.virtualuwb.data.remote.dto

import com.example.virtualuwb.domain.model.RoutePoint
import kotlinx.serialization.Serializable

@Serializable
data class GoogleRoutesRequestDto(
    val origin: RoutePoint,
    val destination: RoutePoint,
    val travelMode: String = "WALK"
)
