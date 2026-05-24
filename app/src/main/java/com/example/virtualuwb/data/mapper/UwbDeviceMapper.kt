package com.example.virtualuwb.data.mapper

import com.example.virtualuwb.data.remote.dto.UwbDeviceDto
import com.example.virtualuwb.data.remote.dto.UpsertUwbDeviceParams
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.UwbDevice
import com.example.virtualuwb.domain.model.UwbRole

object UwbDeviceMapper {

    fun UwbDeviceDto.toDomain(): UwbDevice {
        return UwbDevice(
            id = this.device_code,
            name = this.name,
            role = runCatching { UwbRole.valueOf(this.role) }.getOrDefault(UwbRole.TAG),
            position = GeoPoint(this.latitude, this.longitude),
            updatedAtMillis = System.currentTimeMillis() // MVP placeholder
        )
    }

    fun UwbDevice.toDto(floorId: String? = null): UwbDeviceDto {
        return UwbDeviceDto(
            id = null,
            floor_id = floorId,
            device_code = this.id,
            name = this.name,
            role = this.role.name,
            latitude = this.position.latitude,
            longitude = this.position.longitude,
            is_active = true
        )
    }

    fun UwbDevice.toUpsertRpcParams(
        isActive: Boolean = true
    ): UpsertUwbDeviceParams {
        return UpsertUwbDeviceParams(
            p_device_code = this.id,
            p_name = this.name,
            p_role = this.role.name,
            p_latitude = this.latitude,
            p_longitude = this.longitude,
            p_is_active = isActive
        )
    }
}
