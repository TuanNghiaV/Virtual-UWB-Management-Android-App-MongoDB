package com.example.virtualuwb.data.repository

import android.util.Log
import com.example.virtualuwb.BuildConfig
import com.example.virtualuwb.data.remote.MongoClientProvider
import com.example.virtualuwb.data.remote.dto.MongoResponse
import com.example.virtualuwb.data.remote.dto.MongoUwbDeviceDto
import com.example.virtualuwb.data.remote.dto.MongoUpdatePositionRequestDto
import com.example.virtualuwb.data.remote.dto.MongoUpdatePositionResponseDto
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.UwbDevice
import com.example.virtualuwb.domain.model.UwbRole
import com.example.virtualuwb.domain.repository.UwbRepository
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ApiUwbRepository : UwbRepository {

    private val _devicesFlow = MutableStateFlow<List<UwbDevice>>(emptyList())
    override val devicesFlow: StateFlow<List<UwbDevice>> = _devicesFlow.asStateFlow()

    private val baseUrl = BuildConfig.MONGODB_API_BASE_URL

    suspend fun refreshDevices() {
        try {
            val response: MongoResponse<MongoUwbDeviceDto> = MongoClientProvider.client
                .get("$baseUrl/api/devices")
                .body()

            val mapped = response.items.map { dto ->
                UwbDevice(
                    id = dto.deviceCode,
                    name = dto.name,
                    role = runCatching { UwbRole.valueOf(dto.role) }.getOrDefault(UwbRole.TAG),
                    position = GeoPoint(dto.latitude, dto.longitude),
                    updatedAtMillis = System.currentTimeMillis()
                )
            }.sortedWith(compareBy({ it.role.name }, { it.id }))

            _devicesFlow.value = mapped
        } catch (e: Exception) {
            Log.e("ApiUwbRepository", "Failed to refresh devices from MongoDB API: ${e.message}", e)
            throw e
        }
    }

    override fun getCurrentDevices(): List<UwbDevice> {
        return _devicesFlow.value
    }

    override suspend fun addDevice(device: UwbDevice) {
        Log.w("ApiUwbRepository", "addDevice not supported in read-only MongoDB API mode")
    }

    override suspend fun updateDevice(device: UwbDevice) {
        if (!device.isTag) {
            Log.d("ApiUwbRepository", "Skipping update for anchor: ${device.id}")
            return
        }
        try {
            val response: MongoUpdatePositionResponseDto = MongoClientProvider.client
                .patch("$baseUrl/api/devices/${device.id}/position") {
                    contentType(ContentType.Application.Json)
                    setBody(MongoUpdatePositionRequestDto(
                        latitude = device.position.latitude,
                        longitude = device.position.longitude
                    ))
                }
                .body()

            val updatedDto = response.device
            val updatedDomain = UwbDevice(
                id = updatedDto.deviceCode,
                name = updatedDto.name,
                role = runCatching { UwbRole.valueOf(updatedDto.role) }.getOrDefault(UwbRole.TAG),
                position = GeoPoint(updatedDto.latitude, updatedDto.longitude),
                updatedAtMillis = System.currentTimeMillis()
            )

            // Update local state flow
            _devicesFlow.value = _devicesFlow.value.map {
                if (it.id == updatedDomain.id) updatedDomain else it
            }

            Log.d("ApiUwbRepository", "Updated device position: ${updatedDomain.id} -> (${updatedDomain.position.latitude}, ${updatedDomain.position.longitude})")
        } catch (e: ResponseException) {
            val status = e.response.status.value
            val message = when (status) {
                400 -> "Invalid coordinates (400)"
                404 -> "Device not found (404)"
                500 -> "Backend error (500)"
                else -> "HTTP error $status"
            }
            Log.e("ApiUwbRepository", "Failed to update device position on MongoDB API: $message", e)
            throw Exception(message, e)
        } catch (e: java.io.IOException) {
            val message = "Backend is not running or connection failed"
            Log.e("ApiUwbRepository", "$message: ${e.message}", e)
            throw Exception(message, e)
        } catch (e: Exception) {
            val message = "Unexpected error: ${e.message ?: e::class.simpleName}"
            Log.e("ApiUwbRepository", message, e)
            throw Exception(message, e)
        }
    }

    override suspend fun deleteDevice(deviceId: String) {
        Log.w("ApiUwbRepository", "deleteDevice not supported in MongoDB API mode")
    }

    override suspend fun replaceAll(devices: List<UwbDevice>) {
        devices.filter { it.isTag }.forEach { tag ->
            try {
                updateDevice(tag)
            } catch (e: Exception) {
                Log.e("ApiUwbRepository", "Failed to update tag ${tag.id} in replaceAll: ${e.message}")
            }
        }
    }

    override suspend fun resetToDefault() {
        refreshDevices()
    }
}

