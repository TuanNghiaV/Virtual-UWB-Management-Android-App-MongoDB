package com.example.virtualuwb.simulation

import com.example.virtualuwb.domain.repository.UwbRepository
import com.example.virtualuwb.utils.GeoMath
import com.example.virtualuwb.utils.MapProjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.random.Random

/**
 * Simulation engine that drives TAG movement via **random walk**.
 *
 * ### How it works
 * 1. Each simulation step generates a small random displacement in **metres**
 *    (deltaNorth, deltaEast) for every TAG device.
 * 2. [GeoMath.offsetByApproxMeters] converts the metre-based step into a
 *    Latitude/Longitude delta (flat-Earth approximation, accurate for indoor scale).
 * 3. The resulting position is clamped within the room bounds derived from ANCHOR
 *    positions to prevent TAGs from leaving the room.
 * 4. **ANCHORs are never moved** — they are fixed reference points.
 *
 * The engine does **not** depend on Android framework classes.
 *
 * @param repository     Data source for UWB devices
 * @param externalScope  Coroutine scope that owns the simulation lifecycle
 */
class UwbSimulationEngine(
    private val repository: UwbRepository,
    private val externalScope: CoroutineScope
) {

    // ── Constants ────────────────────────────────────────────────────────

    companion object {
        /** Default interval between simulation steps (milliseconds). */
        const val DEFAULT_INTERVAL_MILLIS = 500L

        /** Default maximum random walk step size (metres per axis). */
        const val DEFAULT_MAX_STEP_METERS = 0.35

        /** Padding from room edges when computing bounds (metres). */
        const val DEFAULT_ROOM_PADDING_METERS = 0.5

        /** Minimum allowed interval to prevent a tight busy-loop. */
        private const val MIN_INTERVAL_MILLIS = 16L
    }

    // ── State ────────────────────────────────────────────────────────────

    private var simulationJob: Job? = null

    private val _isRunning = MutableStateFlow(false)

    /** Observable flag indicating whether the simulation loop is active. */
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Starts the simulation loop.
     *
     * If the engine is already running, this call is a no-op.
     *
     * @param intervalMillis Delay between steps; clamped to at least 16 ms
     * @param maxStepMeters  Maximum displacement per step per axis (metres)
     */
    fun start(
        intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
        maxStepMeters: Double = DEFAULT_MAX_STEP_METERS
    ) {
        // Guard: don't start a second job if one is already active
        if (simulationJob?.isActive == true) return

        _isRunning.value = true

        // Clamp interval to avoid a tight busy-loop
        val safeInterval = intervalMillis.coerceAtLeast(MIN_INTERVAL_MILLIS)

        simulationJob = externalScope.launch {
            try {
                while (isActive) {
                    step(maxStepMeters)
                    delay(safeInterval)
                }
            } finally {
                // Ensure flag is cleared regardless of cancellation reason
                _isRunning.value = false
            }
        }
    }

    /**
     * Stops the simulation loop immediately.
     */
    fun stop() {
        simulationJob?.cancel()
        simulationJob = null
        _isRunning.value = false
    }

    /**
     * Executes a **single** simulation step:
     * - Anchors remain stationary.
     * - Each Tag is displaced by a random walk in metres, converted to Lat/Lon,
     *   and clamped within the room bounds.
     *
     * @param maxStepMeters Maximum displacement per axis (metres).
     *                      Negative values are treated as their absolute value.
     */
    suspend fun step(maxStepMeters: Double = DEFAULT_MAX_STEP_METERS) {
        val currentDevices = repository.getCurrentDevices()
        if (currentDevices.isEmpty()) return

        // Safe absolute step size
        val safeMaxStep = abs(maxStepMeters)

        // ── Derive room bounds from Anchors (preferred) or all devices ──
        val anchors = currentDevices.filter { it.isAnchor }
        val boundsPoints = if (anchors.size >= 2) {
            anchors.map { it.position }
        } else {
            currentDevices.map { it.position }
        }
        val roomBounds = MapProjection.computeBounds(boundsPoints)

        // ── Update each device ──────────────────────────────────────────
        val updatedDevices = currentDevices.map { device ->
            if (device.isAnchor) {
                // Anchors are fixed reference points — never move them
                device
            } else {
                // TAG: random walk in metres, then convert to Lat/Lon
                val deltaNorth = randomSignedMeters(safeMaxStep)
                val deltaEast = randomSignedMeters(safeMaxStep)

                // Convert metre offsets → Lat/Lon delta (flat-Earth approx, fine for indoor)
                val movedPoint = GeoMath.offsetByApproxMeters(
                    origin = device.position,
                    deltaNorthMeters = deltaNorth,
                    deltaEastMeters = deltaEast
                )

                // Clamp within room bounds so the TAG never leaves the room
                val clampedPoint = GeoMath.clampToBounds(
                    point = movedPoint,
                    minLatitude = roomBounds.minLatitude,
                    maxLatitude = roomBounds.maxLatitude,
                    minLongitude = roomBounds.minLongitude,
                    maxLongitude = roomBounds.maxLongitude
                )

                device.withPosition(clampedPoint)
            }
        }

        repository.replaceAll(updatedDevices)
    }

    /**
     * Stops the simulation and resets all devices to their default positions.
     */
    suspend fun reset() {
        stop()
        repository.resetToDefault()
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Returns a uniformly distributed random value in [−maxAbsMeters, +maxAbsMeters).
     *
     * @param maxAbsMeters Maximum absolute displacement. If ≤ 0, returns 0.0.
     */
    private fun randomSignedMeters(maxAbsMeters: Double): Double {
        if (maxAbsMeters <= 0.0) return 0.0
        return Random.nextDouble(from = -maxAbsMeters, until = maxAbsMeters)
    }
}
