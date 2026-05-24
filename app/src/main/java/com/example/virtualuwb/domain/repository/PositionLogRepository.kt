package com.example.virtualuwb.domain.repository

import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.PositionLog

interface PositionLogRepository {
    suspend fun insertLog(log: PositionLog)

    suspend fun insertDevicePosition(
        deviceId: String,
        position: GeoPoint,
        source: String = "SIMULATION"
    )

    suspend fun getLogsForDevice(
        deviceId: String,
        limit: Int = 100
    ): List<PositionLog>
}
