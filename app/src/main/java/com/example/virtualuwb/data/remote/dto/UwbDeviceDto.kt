package com.example.virtualuwb.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UwbDeviceDto(
    val id: String? = null,
    val floor_id: String? = null,
    val device_code: String,
    val name: String,
    val role: String,
    val latitude: Double,
    val longitude: Double,
    val is_active: Boolean = true,
    val created_at: String? = null,
    val updated_at: String? = null
)
