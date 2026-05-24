package com.example.virtualuwb.data.repository

import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.UwbDevice
import com.example.virtualuwb.domain.model.UwbRole
import com.example.virtualuwb.domain.repository.UwbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory fake implementation of [UwbRepository].
 *
 * Pre-loaded with 4 anchors (corners of a small room) and 2 tags (inside the room),
 * centered around a sample location in Ho Chi Minh City, Vietnam.
 *
 * All coordinates use Latitude/Longitude — no pixel or XY data is stored.
 */
class FakeUwbRepository : UwbRepository {

    private val _devicesFlow = MutableStateFlow(createDefaultDevices())

    override val devicesFlow: StateFlow<List<UwbDevice>> = _devicesFlow.asStateFlow()

    override fun getCurrentDevices(): List<UwbDevice> = devicesFlow.value

    override suspend fun addDevice(device: UwbDevice) {
        _devicesFlow.update { current ->
            // Replace if id already exists, otherwise append
            if (current.any { it.id == device.id }) {
                current.map { if (it.id == device.id) device else it }
            } else {
                current + device
            }
        }
    }

    override suspend fun updateDevice(device: UwbDevice) {
        _devicesFlow.update { current ->
            // Replace if id exists, otherwise add to the end
            if (current.any { it.id == device.id }) {
                current.map { if (it.id == device.id) device else it }
            } else {
                current + device
            }
        }
    }

    override suspend fun deleteDevice(deviceId: String) {
        _devicesFlow.update { current ->
            current.filter { it.id != deviceId }
        }
    }

    override suspend fun replaceAll(devices: List<UwbDevice>) {
        _devicesFlow.value = devices
    }

    override suspend fun resetToDefault() {
        _devicesFlow.value = createDefaultDevices()
    }

    companion object {

        /**
         * Creates the default set of UWB devices for the simulated indoor room.
         *
         * Layout (approx. 11 m × 11 m room):
         * ```
         *  A1 (NW) ────────── A2 (NE)
         *  │                        │
         *  │      T1    T2          │
         *  │                        │
         *  A3 (SW) ────────── A4 (SE)
         * ```
         *
         * Center: lat 21.036784, lon 105.834711
         */
        fun createDefaultDevices(): List<UwbDevice> = listOf(
            // ── Anchors (4 corners) ──────────────────────────────────
            UwbDevice(
                id = "anchor-a1",
                name = "Anchor A1",
                role = UwbRole.ANCHOR,
                position = GeoPoint(latitude = 21.037250, longitude = 105.834150)
            ),
            UwbDevice(
                id = "anchor-a2",
                name = "Anchor A2",
                role = UwbRole.ANCHOR,
                position = GeoPoint(latitude = 21.037250, longitude = 105.835250)
            ),
            UwbDevice(
                id = "anchor-a3",
                name = "Anchor A3",
                role = UwbRole.ANCHOR,
                position = GeoPoint(latitude = 21.036300, longitude = 105.834150)
            ),
            UwbDevice(
                id = "anchor-a4",
                name = "Anchor A4",
                role = UwbRole.ANCHOR,
                position = GeoPoint(latitude = 21.036300, longitude = 105.835250)
            ),

            // ── Tags (inside the room) ───────────────────────────────
            UwbDevice(
                id = "tag-t1",
                name = "Tag T1",
                role = UwbRole.TAG,
                position = GeoPoint(latitude = 21.036784, longitude = 105.834711)
            ),
            UwbDevice(
                id = "tag-t2",
                name = "Tag T2",
                role = UwbRole.TAG,
                position = GeoPoint(latitude = 21.036700, longitude = 105.834900)
            )
        )
    }
}
