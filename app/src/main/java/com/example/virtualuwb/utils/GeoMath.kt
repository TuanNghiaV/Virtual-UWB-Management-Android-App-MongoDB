package com.example.virtualuwb.utils

import com.example.virtualuwb.domain.model.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin geographic math utilities for the VirtualUWB indoor positioning system.
 *
 * All functions operate on [GeoPoint] (latitude/longitude) and return results in
 * metres or degrees. No Android framework dependency.
 */
object GeoMath {

    /** Mean radius of the Earth in metres (WGS-84 average). */
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * Approximate metres per degree of latitude (valid at any latitude).
     * 1° latitude ≈ 111 320 m.
     */
    private const val METERS_PER_DEGREE_LAT = 111_320.0

    // ── A. Haversine distance ────────────────────────────────────────────

    /**
     * Calculates the great-circle distance between two geographic points
     * using the **Haversine formula**.
     *
     * ### Haversine formula
     * ```
     * a = sin²(Δlat / 2) + cos(lat1) · cos(lat2) · sin²(Δlon / 2)
     * c = 2 · atan2(√a, √(1 − a))
     * d = R · c
     * ```
     * where R = 6 371 000 m (mean Earth radius).
     *
     * @param from Starting point
     * @param to   Ending point
     * @return Distance in **metres**
     */
    fun haversineDistanceMeters(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)

        // Haversine intermediate value
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2) * sin(dLon / 2)

        // Central angle
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))

        return EARTH_RADIUS_METERS * c
    }

    // ── B. Initial bearing ───────────────────────────────────────────────

    /**
     * Calculates the **initial bearing** (forward azimuth) from [from] to [to].
     *
     * ### Bearing formula
     * ```
     * θ = atan2(
     *       sin(Δlon) · cos(lat2),
     *       cos(lat1) · sin(lat2) − sin(lat1) · cos(lat2) · cos(Δlon)
     *     )
     * ```
     * Result is normalized to **[0, 360)** degrees.
     *
     * - 0°   = North
     * - 90°  = East
     * - 180° = South
     * - 270° = West
     *
     * @param from Origin point
     * @param to   Destination point
     * @return Bearing in degrees, [0.0, 360.0)
     */
    fun initialBearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)

        val x = sin(dLon) * cos(lat2)
        val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        val bearingRad = atan2(x, y)
        return normalizeDegrees(Math.toDegrees(bearingRad))
    }

    // ── C. Normalize 0..360 ──────────────────────────────────────────────

    /**
     * Normalizes an arbitrary angle to the range **[0.0, 360.0)**.
     *
     * Handles negative angles and angles greater than 360°.
     *
     * @param angle Angle in degrees (any value)
     * @return Equivalent angle in [0.0, 360.0)
     */
    fun normalizeDegrees(angle: Double): Double {
        val mod = angle % 360.0
        return if (mod < 0.0) mod + 360.0 else mod
    }

    // ── D. Normalize -180..180 ───────────────────────────────────────────

    /**
     * Normalizes an arbitrary angle to the range **(-180.0, 180.0]**.
     *
     * Useful for computing signed turn angles.
     *
     * @param angle Angle in degrees (any value)
     * @return Equivalent angle in (-180.0, 180.0]
     */
    fun normalizeSignedDegrees(angle: Double): Double {
        val normalized = normalizeDegrees(angle) // 0..360
        return if (normalized > 180.0) normalized - 360.0 else normalized
    }

    // ── E. Arrow rotation ────────────────────────────────────────────────

    /**
     * Computes the rotation angle for a directional arrow icon on screen,
     * as used in a "Find My"–style UI.
     *
     * - **bearing** is the geographic heading from the phone to the target tag
     *   (0° = North, clockwise).
     * - **azimuth** is the direction the phone is currently facing, obtained from
     *   the device compass / rotation sensor (0° = North, clockwise).
     * - **rotation** is the angle the arrow icon must be rotated on the UI so it
     *   points toward the target relative to the phone's orientation.
     *
     * ### Formula
     * ```
     * rotation = normalizeDegrees(bearingToTarget − phoneAzimuth)
     * ```
     *
     * @param bearingToTargetDegrees Geographic bearing to the target [0, 360)
     * @param phoneAzimuthDegrees    Current phone compass heading [0, 360)
     * @return Arrow rotation in degrees [0.0, 360.0)
     */
    fun arrowRotationDegrees(
        bearingToTargetDegrees: Double,
        phoneAzimuthDegrees: Double
    ): Double = normalizeDegrees(bearingToTargetDegrees - phoneAzimuthDegrees)

    // ── F. Offset by approximate metres (random walk helper) ─────────────

    /**
     * Returns a new [GeoPoint] offset from [origin] by the given north/east
     * distances in **metres**.
     *
     * Uses a **flat-Earth approximation** which is accurate enough for indoor
     * and small-distance use-cases (< ~1 km). **Do not** use this for
     * geodesic-accurate calculations over large distances.
     *
     * ### Approximation
     * ```
     * Δlat = deltaNorthMeters / 111 320
     * Δlon = deltaEastMeters  / (111 320 · cos(lat))
     * ```
     * where 111 320 m ≈ 1° of latitude.
     *
     * @param origin          Starting geographic point
     * @param deltaNorthMeters Offset in metres along the north axis (positive = north)
     * @param deltaEastMeters  Offset in metres along the east axis (positive = east)
     * @return New [GeoPoint] with the offset applied
     * @throws IllegalArgumentException if the resulting coordinates are out of valid bounds
     */
    fun offsetByApproxMeters(
        origin: GeoPoint,
        deltaNorthMeters: Double,
        deltaEastMeters: Double
    ): GeoPoint {
        val deltaLat = deltaNorthMeters / METERS_PER_DEGREE_LAT
        val deltaLon = deltaEastMeters /
                (METERS_PER_DEGREE_LAT * cos(Math.toRadians(origin.latitude)))

        val newLatitude = origin.latitude + deltaLat
        val newLongitude = origin.longitude + deltaLon

        // GeoPoint's init block validates the resulting coordinates
        return GeoPoint(latitude = newLatitude, longitude = newLongitude)
    }

    // ── G. Clamp to bounds ───────────────────────────────────────────────

    /**
     * Clamps a [GeoPoint] so that its latitude and longitude fall within the
     * specified bounding box.
     *
     * @param point        The point to clamp
     * @param minLatitude  Southern boundary
     * @param maxLatitude  Northern boundary
     * @param minLongitude Western boundary
     * @param maxLongitude Eastern boundary
     * @return A new [GeoPoint] clamped to the given bounds
     */
    fun clampToBounds(
        point: GeoPoint,
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double
    ): GeoPoint = GeoPoint(
        latitude = point.latitude.coerceIn(minLatitude, maxLatitude),
        longitude = point.longitude.coerceIn(minLongitude, maxLongitude)
    )
}
