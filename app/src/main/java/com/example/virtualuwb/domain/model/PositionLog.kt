package com.example.virtualuwb.domain.model

data class PositionLog(
    val id: Long? = null,
    val deviceId: String,
    val position: GeoPoint,
    val source: String = "SIMULATION",
    val createdAt: String? = null
)
