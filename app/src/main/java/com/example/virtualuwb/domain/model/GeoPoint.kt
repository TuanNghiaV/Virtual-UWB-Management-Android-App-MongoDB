package com.example.virtualuwb.domain.model

/**
 * Represents a geographic coordinate using latitude and longitude.
 *
 * This is the single source of truth for position data in the VirtualUWB system.
 * All positioning is done in Lat/Lon — no pixel or XY coordinate is stored at the model layer.
 * For indoor environments, changes will be at the micro-degree level.
 *
 * @property latitude  Latitude in decimal degrees, valid range: [-90.0, 90.0]
 * @property longitude Longitude in decimal degrees, valid range: [-180.0, 180.0]
 * @throws IllegalArgumentException if latitude or longitude is out of valid range
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude in -90.0..90.0) {
            "Latitude must be in range [-90.0, 90.0], but was $latitude"
        }
        require(longitude in -180.0..180.0) {
            "Longitude must be in range [-180.0, 180.0], but was $longitude"
        }
    }

    companion object {
        /**
         * Checks whether the given latitude and longitude values fall within valid ranges
         * without throwing an exception.
         *
         * @param latitude  Latitude to validate
         * @param longitude Longitude to validate
         * @return true if both values are within valid geographic bounds
         */
        fun isValid(latitude: Double, longitude: Double): Boolean =
            latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}
