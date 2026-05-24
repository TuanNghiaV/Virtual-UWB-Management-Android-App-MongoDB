package com.example.virtualuwb.domain.model

enum class GeofenceEventType {
    ENTER,
    EXIT,
    DWELL
}

data class GeofenceEvent(
    val id: Long? = null,
    val deviceId: String,
    val deviceName: String,
    val geofenceId: String,
    val geofenceName: String,
    val geofenceType: GeofenceType,
    val eventType: GeofenceEventType,
    val position: GeoPoint,
    val createdAt: String? = null
)
