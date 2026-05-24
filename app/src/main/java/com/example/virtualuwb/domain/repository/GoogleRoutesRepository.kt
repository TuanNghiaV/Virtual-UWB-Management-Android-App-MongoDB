package com.example.virtualuwb.domain.repository

import com.example.virtualuwb.domain.model.RoutePoint
import com.example.virtualuwb.domain.model.RouteResult

interface GoogleRoutesRepository {
    suspend fun computeRoute(
        origin: RoutePoint,
        destination: RoutePoint,
        travelMode: String = "WALK"
    ): RouteResult
}
