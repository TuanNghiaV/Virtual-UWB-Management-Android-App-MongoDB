package com.example.virtualuwb.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpsertUwbDeviceParams(
    val p_device_code: String,
    val p_name: String,
    val p_role: String,
    val p_latitude: Double,
    val p_longitude: Double,
    val p_is_active: Boolean = true
)

@Serializable
data class SoftDeleteUwbDeviceParams(
    val p_device_code: String
)
