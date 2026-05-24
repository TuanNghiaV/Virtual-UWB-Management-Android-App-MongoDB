package com.example.virtualuwb.utils

import androidx.compose.ui.geometry.Offset
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.UwbDevice
import kotlin.math.sqrt

// ═══════════════════════════════════════════════════════════════════════════
// GeoBounds
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Defines a geographic bounding box using latitude/longitude extremes.
 *
 * Used to map a set of geographic points onto a finite Canvas area.
 * This class never stores pixel coordinates — it is purely geographic.
 *
 * @property minLatitude  Southern boundary (must be ≤ [maxLatitude])
 * @property maxLatitude  Northern boundary
 * @property minLongitude Western boundary (must be ≤ [maxLongitude])
 * @property maxLongitude Eastern boundary
 */
data class GeoBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
) {
    init {
        require(minLatitude in -90.0..90.0) {
            "minLatitude must be in [-90, 90], was $minLatitude"
        }
        require(maxLatitude in -90.0..90.0) {
            "maxLatitude must be in [-90, 90], was $maxLatitude"
        }
        require(minLongitude in -180.0..180.0) {
            "minLongitude must be in [-180, 180], was $minLongitude"
        }
        require(maxLongitude in -180.0..180.0) {
            "maxLongitude must be in [-180, 180], was $maxLongitude"
        }
        require(minLatitude <= maxLatitude) {
            "minLatitude ($minLatitude) must be ≤ maxLatitude ($maxLatitude)"
        }
        require(minLongitude <= maxLongitude) {
            "minLongitude ($minLongitude) must be ≤ maxLongitude ($maxLongitude)"
        }
    }

    /** Total latitude span of the bounding box. */
    val latitudeSpan: Double get() = maxLatitude - minLatitude

    /** Total longitude span of the bounding box. */
    val longitudeSpan: Double get() = maxLongitude - minLongitude

    /** Geographic center of the bounding box. */
    val center: GeoPoint
        get() = GeoPoint(
            latitude = (minLatitude + maxLatitude) / 2.0,
            longitude = (minLongitude + maxLongitude) / 2.0
        )

    /**
     * Checks whether a [GeoPoint] lies within (or on the edge of) this bounding box.
     */
    fun contains(point: GeoPoint): Boolean =
        point.latitude in minLatitude..maxLatitude &&
                point.longitude in minLongitude..maxLongitude

    /**
     * Returns a new [GeoBounds] expanded by [ratio] of the current span in every direction.
     *
     * For example, `ratio = 0.1` expands each edge outward by 10% of the span,
     * effectively making the total span 120% of the original.
     *
     * If the span is zero in either axis, a small fallback (1e-7 degrees ≈ ~1 cm)
     * is used to avoid a degenerate zero-size bounding box.
     */
    fun expandByRatio(ratio: Double): GeoBounds {
        // Fallback for zero-size spans (~1 cm at the equator)
        val fallback = 1e-7

        val latExpand = if (latitudeSpan == 0.0) fallback else latitudeSpan * ratio
        val lonExpand = if (longitudeSpan == 0.0) fallback else longitudeSpan * ratio

        return GeoBounds(
            minLatitude = (minLatitude - latExpand).coerceAtLeast(-90.0),
            maxLatitude = (maxLatitude + latExpand).coerceAtMost(90.0),
            minLongitude = (minLongitude - lonExpand).coerceAtLeast(-180.0),
            maxLongitude = (maxLongitude + lonExpand).coerceAtMost(180.0)
        )
    }

    /**
     * Ensures the bounding box has at least the specified minimum spans.
     *
     * When all points share the same coordinate (or there is only one point),
     * the spans would be zero, causing division-by-zero during projection.
     * This function expands the bounds equally from the center to guarantee
     * a valid, non-degenerate bounding box.
     *
     * @param minLatSpan Minimum allowed latitude span
     * @param minLonSpan Minimum allowed longitude span
     */
    fun withMinimumSpan(minLatSpan: Double, minLonSpan: Double): GeoBounds {
        val ctr = center

        val halfLat = if (latitudeSpan < minLatSpan) minLatSpan / 2.0 else latitudeSpan / 2.0
        val halfLon = if (longitudeSpan < minLonSpan) minLonSpan / 2.0 else longitudeSpan / 2.0

        return GeoBounds(
            minLatitude = (ctr.latitude - halfLat).coerceAtLeast(-90.0),
            maxLatitude = (ctr.latitude + halfLat).coerceAtMost(90.0),
            minLongitude = (ctr.longitude - halfLon).coerceAtLeast(-180.0),
            maxLongitude = (ctr.longitude + halfLon).coerceAtMost(180.0)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// MapProjection
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Pure projection utilities for converting between geographic coordinates
 * (Latitude/Longitude) and Jetpack Compose Canvas pixel coordinates (Offset).
 *
 * ### Coordinate mapping rules
 * - **Longitude → X axis** (left = west, right = east)
 * - **Latitude  → Y axis** with **inverted direction**: because Canvas Y increases
 *   downward but latitude increases northward, higher latitudes are drawn at
 *   smaller Y values (closer to the top of the screen).
 *
 * No pixel data is ever stored in domain models. Projection is a pure, stateless
 * function performed at the UI/rendering layer only.
 */
object MapProjection {

    // ── A. computeBounds ─────────────────────────────────────────────────

    /**
     * Computes a [GeoBounds] that encloses all given [points].
     *
     * The resulting bounds are guaranteed to have a non-zero span
     * (via [GeoBounds.withMinimumSpan]) and include a 15% geographic
     * padding (via [GeoBounds.expandByRatio]).
     *
     * @param points Non-empty list of geographic points
     * @return Enclosing bounds with padding
     * @throws IllegalArgumentException if [points] is empty
     */
    fun computeBounds(points: List<GeoPoint>): GeoBounds {
        require(points.isNotEmpty()) { "Cannot compute bounds from an empty list of points" }

        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }

        return GeoBounds(
            minLatitude = minLat,
            maxLatitude = maxLat,
            minLongitude = minLon,
            maxLongitude = maxLon
        )
            .withMinimumSpan(minLatSpan = 0.00001, minLonSpan = 0.00001)
            .expandByRatio(0.15)
    }

    // ── B. latLonToCanvasOffset ──────────────────────────────────────────

    /**
     * Projects a geographic [GeoPoint] onto a Compose Canvas as an [Offset].
     *
     * ### Y-axis inversion
     * Canvas Y grows **downward**, but latitude grows **northward**.
     * To render north at the top of the screen:
     * ```
     * normalizedY = (bounds.maxLatitude − point.latitude) / latitudeSpan
     * ```
     * This maps the maximum latitude (north) to Y = 0 (top) and the
     * minimum latitude (south) to Y = drawableHeight (bottom).
     *
     * @param point        Geographic point to project
     * @param bounds       Geographic bounding box defining the visible area
     * @param canvasWidth  Total canvas width in pixels (must be > 0)
     * @param canvasHeight Total canvas height in pixels (must be > 0)
     * @param paddingPx    Inset padding on each side in pixels (must be ≥ 0)
     * @return [Offset] in canvas pixel coordinates
     */
    fun latLonToCanvasOffset(
        point: GeoPoint,
        bounds: GeoBounds,
        canvasWidth: Float,
        canvasHeight: Float,
        paddingPx: Float = 32f
    ): Offset {
        require(canvasWidth > 0f) { "canvasWidth must be > 0, was $canvasWidth" }
        require(canvasHeight > 0f) { "canvasHeight must be > 0, was $canvasHeight" }
        require(paddingPx >= 0f) { "paddingPx must be ≥ 0, was $paddingPx" }

        val drawableWidth = canvasWidth - paddingPx * 2f
        val drawableHeight = canvasHeight - paddingPx * 2f

        // If padding is too large for the canvas, fall back to center
        if (drawableWidth <= 0f || drawableHeight <= 0f) {
            return Offset(canvasWidth / 2f, canvasHeight / 2f)
        }

        // Longitude → X (left to right, west to east)
        val normalizedX = (point.longitude - bounds.minLongitude) / bounds.longitudeSpan

        // Latitude → Y (INVERTED: north/top = small Y, south/bottom = large Y)
        val normalizedY = (bounds.maxLatitude - point.latitude) / bounds.latitudeSpan

        val x = paddingPx + normalizedX * drawableWidth
        val y = paddingPx + normalizedY * drawableHeight

        return Offset(x.toFloat(), y.toFloat())
    }

    // ── C. canvasOffsetToLatLon ──────────────────────────────────────────

    /**
     * Inverse projection: converts a Canvas [Offset] (pixel position) back
     * to a [GeoPoint].
     *
     * Useful for tap-to-place interactions (e.g. adding a device or geofence
     * by tapping on the map canvas).
     *
     * The normalized values are clamped to [0, 1] so the returned point
     * always lies within [bounds].
     *
     * @param offset       Pixel position on the canvas
     * @param bounds       Geographic bounding box of the visible area
     * @param canvasWidth  Total canvas width in pixels
     * @param canvasHeight Total canvas height in pixels
     * @param paddingPx    Inset padding on each side in pixels
     * @return Corresponding [GeoPoint]
     */
    fun canvasOffsetToLatLon(
        offset: Offset,
        bounds: GeoBounds,
        canvasWidth: Float,
        canvasHeight: Float,
        paddingPx: Float = 32f
    ): GeoPoint {
        require(canvasWidth > 0f) { "canvasWidth must be > 0, was $canvasWidth" }
        require(canvasHeight > 0f) { "canvasHeight must be > 0, was $canvasHeight" }
        require(paddingPx >= 0f) { "paddingPx must be ≥ 0, was $paddingPx" }

        val drawableWidth = canvasWidth - paddingPx * 2f
        val drawableHeight = canvasHeight - paddingPx * 2f

        // If padding is too large for the canvas, fall back to bounds center
        if (drawableWidth <= 0f || drawableHeight <= 0f) {
            return bounds.center
        }

        // Clamp normalized values to [0, 1] to stay within bounds (Float literals)
        val normalizedX = ((offset.x - paddingPx) / drawableWidth).coerceIn(0f, 1f)
        val normalizedY = ((offset.y - paddingPx) / drawableHeight).coerceIn(0f, 1f)

        // Longitude: straightforward left-to-right mapping
        val longitude = bounds.minLongitude + normalizedX.toDouble() * bounds.longitudeSpan

        // Latitude: inverted — top of canvas (normalizedY = 0) = maxLatitude (north)
        val latitude = bounds.maxLatitude - normalizedY.toDouble() * bounds.latitudeSpan

        return GeoPoint(latitude = latitude, longitude = longitude)
    }

    // ── D. distancePixels ────────────────────────────────────────────────

    /**
     * Computes the Euclidean pixel distance between two [GeoPoint]s after
     * projecting them onto the canvas.
     *
     * @return Distance in pixels
     */
    fun distancePixels(
        from: GeoPoint,
        to: GeoPoint,
        bounds: GeoBounds,
        canvasWidth: Float,
        canvasHeight: Float,
        paddingPx: Float = 32f
    ): Float {
        val offsetFrom = latLonToCanvasOffset(from, bounds, canvasWidth, canvasHeight, paddingPx)
        val offsetTo = latLonToCanvasOffset(to, bounds, canvasWidth, canvasHeight, paddingPx)

        val dx = offsetTo.x - offsetFrom.x
        val dy = offsetTo.y - offsetFrom.y

        return sqrt(dx * dx + dy * dy)
    }

    // ── E. safeComputeBoundsForDevices ────────────────────────────────────

    /**
     * Safely computes [GeoBounds] from a list of [UwbDevice]s.
     *
     * If the list is empty, returns default bounds centered on a sample
     * location in Ho Chi Minh City, Vietnam (10.762622, 106.660172)
     * with a small span suitable for indoor visualization.
     *
     * @param devices List of UWB devices (may be empty)
     * @return Enclosing geographic bounds
     */
    fun safeComputeBoundsForDevices(devices: List<UwbDevice>): GeoBounds {
        if (devices.isEmpty()) {
            // Default: sample indoor location in HCMC, Vietnam
            val centerLat = 10.762622
            val centerLon = 106.660172
            val halfSpan = 0.0001 / 2.0
            return GeoBounds(
                minLatitude = centerLat - halfSpan,
                maxLatitude = centerLat + halfSpan,
                minLongitude = centerLon - halfSpan,
                maxLongitude = centerLon + halfSpan
            )
        }
        return computeBounds(devices.map { it.position })
    }
}
