package com.example.virtualuwb.domain.model

/**
 * Represents a single UWB device (anchor or tag) in the indoor positioning system.
 *
 * Position is stored exclusively as [GeoPoint] (Lat/Lon).
 * No pixel or XY screen coordinates are kept at the model layer.
 *
 * @property id              Unique identifier for the device
 * @property name            Human-readable display name
 * @property role            Whether the device is an [UwbRole.ANCHOR] or [UwbRole.TAG]
 * @property position        Geographic position as latitude/longitude
 * @property updatedAtMillis Epoch millis of the last position update
 */
data class UwbDevice(
    val id: String,
    val name: String,
    val role: UwbRole,
    val position: GeoPoint,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    // ── Convenience accessors ────────────────────────────────────────────

    /** Shortcut for [position.latitude]. */
    val latitude: Double get() = position.latitude

    /** Shortcut for [position.longitude]. */
    val longitude: Double get() = position.longitude

    /** True if this device is a fixed anchor. */
    val isAnchor: Boolean get() = role == UwbRole.ANCHOR

    /** True if this device is a mobile tag. */
    val isTag: Boolean get() = role == UwbRole.TAG

    // ── Position update ──────────────────────────────────────────────────

    /**
     * Returns a copy of this device with an updated [position] and a fresh timestamp.
     *
     * @param newPosition The new geographic position to apply
     * @return A new [UwbDevice] instance with the updated position and current time
     */
    fun withPosition(newPosition: GeoPoint): UwbDevice =
        copy(position = newPosition, updatedAtMillis = System.currentTimeMillis())
}
