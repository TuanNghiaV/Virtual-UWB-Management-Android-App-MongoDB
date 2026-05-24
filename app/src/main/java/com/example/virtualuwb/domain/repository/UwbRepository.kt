package com.example.virtualuwb.domain.repository

import com.example.virtualuwb.domain.model.UwbDevice
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository contract for managing UWB devices (anchors and tags).
 *
 * Exposes a reactive [StateFlow] of the current device list, along with
 * suspend functions for CRUD operations. Implementations may be backed
 * by in-memory data, a local database, or a remote source.
 */
interface UwbRepository {

    /** Observable stream of the current device list. */
    val devicesFlow: StateFlow<List<UwbDevice>>

    /** Returns a snapshot of the current device list. */
    fun getCurrentDevices(): List<UwbDevice>

    /** Adds a device. If a device with the same [UwbDevice.id] exists, it is replaced. */
    suspend fun addDevice(device: UwbDevice)

    /** Updates an existing device. If no device with the same id exists, it is added. */
    suspend fun updateDevice(device: UwbDevice)

    /** Removes the device with the given [deviceId], if present. */
    suspend fun deleteDevice(deviceId: String)

    /** Replaces the entire device list with [devices]. */
    suspend fun replaceAll(devices: List<UwbDevice>)

    /** Resets the device list back to its default/initial state. */
    suspend fun resetToDefault()
}
