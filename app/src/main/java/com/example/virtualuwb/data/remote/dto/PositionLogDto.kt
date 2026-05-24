package com.example.virtualuwb.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PositionLogDto(
    val id: Long? = null,
    val device_code: String,
    val latitude: Double,
    val longitude: Double,
    val source: String = "SIMULATION",
    val created_at: String? = null
)

@Serializable
data class InsertPositionLogParams(
    val p_device_code: String,
    val p_latitude: Double,
    val p_longitude: Double,
    val p_source: String = "SIMULATION"
)

@Serializable
data class GetPositionLogsParams(
    val p_device_code: String,
    val p_limit: Int = 100
)
