package com.example.virtualuwb.data.repository

import com.example.virtualuwb.data.mapper.UwbDeviceMapper.toDomain
import com.example.virtualuwb.data.mapper.UwbDeviceMapper.toUpsertRpcParams
import com.example.virtualuwb.data.remote.dto.SoftDeleteUwbDeviceParams
import com.example.virtualuwb.data.remote.dto.UwbDeviceDto
import com.example.virtualuwb.data.remote.supabase.SupabaseClientProvider
import com.example.virtualuwb.domain.model.UwbDevice
import com.example.virtualuwb.domain.repository.UwbRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SupabaseUwbRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client
) : UwbRepository {

    private val _devicesFlow = MutableStateFlow<List<UwbDevice>>(emptyList())
    override val devicesFlow: StateFlow<List<UwbDevice>> = _devicesFlow.asStateFlow()

    suspend fun refreshDevices() {
        val dtoList = client.postgrest.from("uwb_devices")
            .select {
                filter {
                    eq("is_active", true)
                }
            }
            .decodeList<UwbDeviceDto>()

        val mappedDevices = dtoList.map { it.toDomain() }
            .sortedWith(compareBy({ it.role.name }, { it.id }))

        _devicesFlow.value = mappedDevices
    }

    override fun getCurrentDevices(): List<UwbDevice> {
        return _devicesFlow.value
    }

    override suspend fun addDevice(device: UwbDevice) {
        client.postgrest.rpc(
            "upsert_uwb_device_for_demo_floor",
            device.toUpsertRpcParams(isActive = true)
        )
        refreshDevices()
    }

    override suspend fun updateDevice(device: UwbDevice) {
        client.postgrest.rpc(
            "upsert_uwb_device_for_demo_floor",
            device.toUpsertRpcParams(isActive = true)
        )
        refreshDevices()
    }

    override suspend fun deleteDevice(deviceId: String) {
        client.postgrest.rpc(
            "soft_delete_uwb_device",
            SoftDeleteUwbDeviceParams(p_device_code = deviceId)
        )
        refreshDevices()
    }

    override suspend fun replaceAll(devices: List<UwbDevice>) {
        devices.forEach { device ->
            client.postgrest.rpc(
                "upsert_uwb_device_for_demo_floor",
                device.toUpsertRpcParams(isActive = true)
            )
        }
        refreshDevices()
    }

    override suspend fun resetToDefault() {
        // Default seed lies in schema.sql.
        // We do not re-seed remotely here, we simply fetch.
        refreshDevices()
    }

    suspend fun testConnection(): Result<Int> {
        return try {
            refreshDevices()
            Result.success(_devicesFlow.value.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
