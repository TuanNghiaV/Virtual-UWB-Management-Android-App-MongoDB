package com.example.virtualuwb.domain.model

enum class GeofenceType {
    ROOM,
    SAFE_ZONE,
    RESTRICTED_ZONE
}

data class Geofence(
    val id: String,
    val name: String,
    val type: GeofenceType,
    val vertices: List<GeoPoint>
) {
    init {
        require(vertices.size >= 3) { "A geofence must have at least 3 vertices." }
    }

    val isRestricted: Boolean
        get() = type == GeofenceType.RESTRICTED_ZONE
}
