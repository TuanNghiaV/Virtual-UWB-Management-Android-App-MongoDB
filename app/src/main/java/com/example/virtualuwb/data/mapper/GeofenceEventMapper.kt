package com.example.virtualuwb.data.mapper

import com.example.virtualuwb.data.remote.dto.GeofenceEventDto
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.GeofenceEvent
import com.example.virtualuwb.domain.model.GeofenceEventType
import com.example.virtualuwb.domain.model.GeofenceType

object GeofenceEventMapper {

    fun GeofenceEventDto.toDomain(): GeofenceEvent {
        return GeofenceEvent(
            id = this.id,
            deviceId = this.device_code,
            deviceName = this.device_name,
            geofenceId = this.geofence_code,
            geofenceName = this.geofence_name,
            geofenceType = runCatching { GeofenceType.valueOf(this.geofence_type) }
                .getOrDefault(GeofenceType.RESTRICTED_ZONE),
            eventType = runCatching { GeofenceEventType.valueOf(this.event_type) }
                .getOrDefault(GeofenceEventType.ENTER),
            position = GeoPoint(this.latitude, this.longitude),
            createdAt = this.created_at
        )
    }
}
