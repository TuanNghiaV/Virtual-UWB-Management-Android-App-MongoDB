package com.example.virtualuwb.presentation.viewmodel

import com.example.virtualuwb.domain.model.DataSourceMode
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.Geofence
import com.example.virtualuwb.domain.model.RouteResult
import com.example.virtualuwb.domain.model.UwbDevice
import com.example.virtualuwb.utils.GeoBounds
import com.example.virtualuwb.utils.MapProjection

/**
 * Immutable UI state for the Indoor Map screen.
 *
 * All position data remains in Latitude/Longitude.
 * Pixel conversion happens only at the Compose rendering layer.
 *
 * @property devices              Full list of UWB devices (anchors + tags)
 * @property isSimulationRunning  Whether the simulation engine is active
 * @property selectedTagId        ID of the currently selected tag (nullable)
 * @property bounds               Geographic bounding box for map projection
 * @property phonePosition        Simulated phone position (Lat/Lon)
 * @property phoneAzimuthDegrees  Simulated phone heading (0°=North, clockwise)
 * @property isPhoneGpsLocation   True if the phone position is from real GPS
 */
data class MapUiState(
    val devices: List<UwbDevice> = emptyList(),
    val isSimulationRunning: Boolean = false,
    val selectedTagId: String? = null,
    val bounds: GeoBounds = MapProjection.safeComputeBoundsForDevices(emptyList()),
    val phonePosition: GeoPoint = GeoPoint(
        latitude = 21.036784,
        longitude = 105.834711
    ),
    val phoneAzimuthDegrees: Double = 0.0,
    val isPhoneGpsLocation: Boolean = false,
    val tagTrails: Map<String, List<GeoPoint>> = emptyMap(),
    val geofences: List<Geofence> = emptyList(),
    val dataSourceMode: DataSourceMode = DataSourceMode.SUPABASE,
    val isRemoteLoading: Boolean = false,
    val remoteStatusMessage: String? = null,
    val isPositionLoggingEnabled: Boolean = true,
    val lastPositionLogStatus: String? = null,
    val lastGeofenceEventStatus: String? = null,
    val tagMovementSpeed: Int = 0,
    val routeToSelectedTag: RouteResult? = null,
    val isRouteLoading: Boolean = false,
    val routeError: String? = null
) {
    // ── Convenience properties ───────────────────────────────────────────

    val restrictedGeofences: List<Geofence> get() = geofences.filter { it.isRestricted }

    /** All fixed anchor devices. */
    val anchors: List<UwbDevice> get() = devices.filter { it.isAnchor }

    /** All mobile tag devices. */
    val tags: List<UwbDevice> get() = devices.filter { it.isTag }

    /** The currently selected tag, or null if none is selected or the id is stale. */
    val selectedTag: UwbDevice? get() = tags.firstOrNull { it.id == selectedTagId }

    /** True when there is at least one device in the list. */
    val hasDevices: Boolean get() = devices.isNotEmpty()

    /** Number of anchors. */
    val anchorCount: Int get() = anchors.size

    /** Number of tags. */
    val tagCount: Int get() = tags.size
}
