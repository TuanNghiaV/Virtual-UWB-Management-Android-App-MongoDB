package com.example.virtualuwb.domain.repository

import com.example.virtualuwb.domain.model.GeofenceEvent

interface GeofenceEventRepository {
    suspend fun evaluateForDevice(deviceId: String): Int

    suspend fun getRecentEvents(limit: Int = 50): List<GeofenceEvent>
}
