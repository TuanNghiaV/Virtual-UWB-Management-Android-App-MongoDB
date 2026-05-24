package com.example.virtualuwb.data.mapper

import com.example.virtualuwb.data.remote.dto.InsertPositionLogParams
import com.example.virtualuwb.data.remote.dto.PositionLogDto
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.PositionLog

object PositionLogMapper {

    fun PositionLogDto.toDomain(): PositionLog {
        return PositionLog(
            id = this.id,
            deviceId = this.device_code,
            position = GeoPoint(latitude = this.latitude, longitude = this.longitude),
            source = this.source,
            createdAt = this.created_at
        )
    }

    fun PositionLog.toInsertParams(): InsertPositionLogParams {
        return InsertPositionLogParams(
            p_device_code = this.deviceId,
            p_latitude = this.position.latitude,
            p_longitude = this.position.longitude,
            p_source = this.source
        )
    }
}
