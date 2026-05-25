package com.example.virtualuwb.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.example.virtualuwb.data.repository.FakeGeofenceRepository
import com.example.virtualuwb.data.repository.FakeUwbRepository
import com.example.virtualuwb.data.repository.SupabaseGeofenceEventRepository
import com.example.virtualuwb.data.repository.SupabaseGeofenceRepository
import com.example.virtualuwb.data.repository.SupabasePositionLogRepository
import com.example.virtualuwb.data.repository.SupabaseUwbRepository
import com.example.virtualuwb.data.repository.SupabaseGoogleRoutesRepository
import com.example.virtualuwb.data.repository.ApiGoogleRoutesRepository
import com.example.virtualuwb.data.repository.ApiUwbRepository
import com.example.virtualuwb.data.repository.ApiGeofenceRepository
import com.example.virtualuwb.domain.model.RoutePoint
import com.example.virtualuwb.domain.model.RouteResult
import com.example.virtualuwb.domain.model.DataSourceMode
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.Geofence
import com.example.virtualuwb.domain.model.UwbDevice
import com.example.virtualuwb.domain.repository.GoogleRoutesRepository
import com.example.virtualuwb.domain.repository.GeofenceRepository
import com.example.virtualuwb.domain.repository.UwbRepository
import com.example.virtualuwb.simulation.UwbSimulationEngine
import com.example.virtualuwb.utils.GeoMath
import com.example.virtualuwb.utils.GeofenceMath
import com.example.virtualuwb.utils.MapProjection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * ViewModel for the Indoor Map screen.
 *
 * Combines the device list, simulation status, tag selection, simulated
 * phone state, and tag movement trails into a single [MapUiState] exposed
 * as a [StateFlow]. All position data stays in Lat/Lon; pixel projection
 * is deferred to the Compose UI layer.
 */
class MapViewModel : ViewModel() {

    private val localRepository = FakeUwbRepository()
    private val remoteRepository = SupabaseUwbRepository()
    private val apiRepository = ApiUwbRepository()

    private val localGeofenceRepository = FakeGeofenceRepository()
    private val remoteGeofenceRepository = SupabaseGeofenceRepository()
    private val apiGeofenceRepository = ApiGeofenceRepository()
    private val positionLogRepository = SupabasePositionLogRepository()
    private val geofenceEventRepository = SupabaseGeofenceEventRepository()

    private var activeRepository: UwbRepository = apiRepository
    private var activeGeofenceRepository: GeofenceRepository = apiGeofenceRepository

    private var simulationEngine = UwbSimulationEngine(activeRepository, viewModelScope)

    private val dataSourceModeFlow = MutableStateFlow(DataSourceMode.API_MONGODB)
    private val isRemoteLoadingFlow = MutableStateFlow(false)
    private val remoteStatusMessageFlow = MutableStateFlow<String?>(null)
    
    private val isPositionLoggingEnabledFlow = MutableStateFlow(true)
    private val lastPositionLogStatusFlow = MutableStateFlow<String?>(null)
    private val lastGeofenceEventStatusFlow = MutableStateFlow<String?>(null)
    private var lastPositionLogAtMillis: Long = 0L
    private val tagMovementSpeedFlow = MutableStateFlow(0)
    
    private val routeToSelectedTagFlow = MutableStateFlow<RouteResult?>(null)
    private val isRouteLoadingFlow = MutableStateFlow(false)
    private val routeErrorFlow = MutableStateFlow<String?>(null)
    private val supabaseGoogleRoutesRepository = SupabaseGoogleRoutesRepository()
    private val apiGoogleRoutesRepository = ApiGoogleRoutesRepository()
    
    private val devicesBridgeFlow = MutableStateFlow<List<UwbDevice>>(localRepository.getCurrentDevices())
    private val geofencesBridgeFlow = MutableStateFlow<List<Geofence>>(localGeofenceRepository.getCurrentGeofences())
    private val simulationRunningFlow = MutableStateFlow(false)

    private var repositoryCollectJob: Job? = null
    private var geofenceCollectJob: Job? = null
    private var simulationRunningCollectJob: Job? = null

    /** Tracks which tag the user has selected; auto-selects first tag when null/stale. */
    private val selectedTagIdFlow = MutableStateFlow<String?>(null)

    /** Simulated phone position (Lat/Lon). */
    private val phonePositionFlow = MutableStateFlow(
        GeoPoint(latitude = 21.036784, longitude = 105.834711)
    )

    /** Simulated phone heading in degrees (0° = North, clockwise). */
    private val phoneAzimuthFlow = MutableStateFlow(0.0)

    private val isPhoneGpsLocationFlow = MutableStateFlow(false)

    /** Tag movement trails: deviceId → list of recent GeoPoint positions. */
    private val tagTrailsFlow = MutableStateFlow<Map<String, List<GeoPoint>>>(emptyMap())

    /** Maximum number of trail points kept per tag. */
    private companion object {
        const val MAX_TRAIL_POINTS = 40
        const val POSITION_LOG_INTERVAL_MILLIS = 2_000L
        const val AUTO_SIMULATION_INTERVAL_MILLIS = 1_000L
        const val BASE_SIMULATION_STEP_METERS = 0.18
        const val TAG = "MapViewModel"
    }

    init {
        startCollectingRepository(activeRepository)
        startCollectingGeofenceRepository(activeGeofenceRepository)
        startCollectingSimulation(simulationEngine)

        viewModelScope.launch {
            isRemoteLoadingFlow.value = true
            remoteStatusMessageFlow.value = "Connecting to MongoDB API..."

            try {
                apiRepository.refreshDevices()
                apiGeofenceRepository.refreshGeofences()
                remoteStatusMessageFlow.value = "MongoDB API connected. Devices: ${apiRepository.getCurrentDevices().size}"
            } catch (e: Exception) {
                remoteStatusMessageFlow.value = "MongoDB API failed: ${e.message ?: e::class.simpleName}"
            } finally {
                isRemoteLoadingFlow.value = false
            }
        }

        // Collect device updates to build position trails for each tag.
        viewModelScope.launch {
            devicesBridgeFlow.collect { devices ->
                val tags = devices.filter { it.isTag }
                tagTrailsFlow.value = buildMap {
                    for (tag in tags) {
                        val oldTrail = tagTrailsFlow.value[tag.id].orEmpty()
                        val newTrail = if (oldTrail.lastOrNull() == tag.position) {
                            oldTrail
                        } else {
                            (oldTrail + tag.position).takeLast(MAX_TRAIL_POINTS)
                        }
                        put(tag.id, newTrail)
                    }
                }
                logTagPositionsIfNeeded(devices)
            }
        }

        syncSimulationToSpeed()
    }

    private suspend fun logTagPositionsIfNeeded(devices: List<UwbDevice>) {
        if (dataSourceModeFlow.value != DataSourceMode.SUPABASE) return
        if (!isPositionLoggingEnabledFlow.value) return

        val now = System.currentTimeMillis()
        if (now - lastPositionLogAtMillis < POSITION_LOG_INTERVAL_MILLIS) return

        val tags = devices.filter { it.isTag }
        if (tags.isEmpty()) return

        try {
            // Log positions
            tags.forEach { tag ->
                positionLogRepository.insertDevicePosition(
                    deviceId = tag.id,
                    position = tag.position,
                    source = "SIMULATION"
                )
            }
            
            // Evaluate geofence events
            val totalEvents = tags.sumOf { tag ->
                geofenceEventRepository.evaluateForDevice(tag.id)
            }

            lastPositionLogAtMillis = now
            lastPositionLogStatusFlow.value = "Logged ${tags.size} tag positions"
            lastGeofenceEventStatusFlow.value = if (totalEvents > 0) {
                "Created $totalEvents geofence events"
            } else {
                "No new geofence events"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = "Position log/event failed: ${e.message ?: e::class.simpleName}"
            lastPositionLogStatusFlow.value = errorMsg
            lastGeofenceEventStatusFlow.value = errorMsg
        }
    }

    private fun startCollectingRepository(repository: UwbRepository) {
        repositoryCollectJob?.cancel()
        repositoryCollectJob = viewModelScope.launch {
            repository.devicesFlow.collect { devices ->
                devicesBridgeFlow.value = devices
            }
        }
    }

    private fun startCollectingGeofenceRepository(repository: GeofenceRepository) {
        geofenceCollectJob?.cancel()
        geofenceCollectJob = viewModelScope.launch {
            repository.geofencesFlow.collect { geofences ->
                geofencesBridgeFlow.value = geofences
            }
        }
    }

    private fun startCollectingSimulation(engine: UwbSimulationEngine) {
        simulationRunningCollectJob?.cancel()
        simulationRunningCollectJob = viewModelScope.launch {
            engine.isRunning.collect { isRunning ->
                simulationRunningFlow.value = isRunning
            }
        }
    }

    // ── State Bundles for UI State ───────────────────────────────────────

    private data class DeviceStateBundle(
        val devices: List<UwbDevice>,
        val isRunning: Boolean,
        val selectedTagId: String?,
        val tagMovementSpeed: Int
    )

    private data class PhoneStateBundle(
        val phonePosition: GeoPoint,
        val phoneAzimuthDegrees: Double,
        val tagTrails: Map<String, List<GeoPoint>>,
        val isPhoneGpsLocation: Boolean
    )

    private data class RemoteStateBundle(
        val dataSourceMode: DataSourceMode,
        val isRemoteLoading: Boolean,
        val remoteStatusMessage: String?,
        val isPositionLoggingEnabled: Boolean,
        val lastPositionLogStatus: String?,
        val lastGeofenceEventStatus: String?
    )

    private val deviceStateFlow = combine(
        devicesBridgeFlow,
        simulationRunningFlow,
        selectedTagIdFlow,
        tagMovementSpeedFlow
    ) { devices, isRunning, selectedTagId, tagMovementSpeed ->
        DeviceStateBundle(
            devices = devices,
            isRunning = isRunning,
            selectedTagId = selectedTagId,
            tagMovementSpeed = tagMovementSpeed
        )
    }

    private val phoneStateFlow = combine(
        phonePositionFlow,
        phoneAzimuthFlow,
        tagTrailsFlow,
        isPhoneGpsLocationFlow
    ) { phonePosition, phoneAzimuthDegrees, tagTrails, isGps ->
        PhoneStateBundle(
            phonePosition = phonePosition,
            phoneAzimuthDegrees = phoneAzimuthDegrees,
            tagTrails = tagTrails,
            isPhoneGpsLocation = isGps
        )
    }

    private val remoteStateFlow = combine(
        combine(
            dataSourceModeFlow,
            isRemoteLoadingFlow,
            remoteStatusMessageFlow
        ) { mode, isLoading, statusMsg -> 
            Triple(mode, isLoading, statusMsg)
        },
        combine(
            isPositionLoggingEnabledFlow,
            lastPositionLogStatusFlow,
            lastGeofenceEventStatusFlow
        ) { isLoggingEnabled, logStatus, eventStatus ->
            Triple(isLoggingEnabled, logStatus, eventStatus)
        }
    ) { (mode, isLoading, statusMsg), (isLoggingEnabled, logStatus, eventStatus) ->
        RemoteStateBundle(
            dataSourceMode = mode,
            isRemoteLoading = isLoading,
            remoteStatusMessage = statusMsg,
            isPositionLoggingEnabled = isLoggingEnabled,
            lastPositionLogStatus = logStatus,
            lastGeofenceEventStatus = eventStatus
        )
    }

    private data class RouteStateBundle(
        val routeToSelectedTag: RouteResult?,
        val isRouteLoading: Boolean,
        val routeError: String?
    )

    private val routeStateFlow = combine(
        routeToSelectedTagFlow,
        isRouteLoadingFlow,
        routeErrorFlow
    ) { route, isLoading, error ->
        RouteStateBundle(route, isLoading, error)
    }

    /**
     * Combined UI state stream.
     */
    val uiState: StateFlow<MapUiState> = combine(
        combine(
            deviceStateFlow,
            phoneStateFlow
        ) { deviceState, phoneState ->
            deviceState to phoneState
        },
        combine(
            geofencesBridgeFlow,
            remoteStateFlow
        ) { geofences, remoteState ->
            geofences to remoteState
        },
        routeStateFlow
    ) { (deviceState, phoneState), (geofences, remoteState), routeState ->

        val devices = deviceState.devices
        val tags = devices.filter { it.isTag }

        // Auto-select: keep current selection if valid, otherwise pick the first tag
        val validSelectedTagId = deviceState.selectedTagId
            ?.takeIf { id -> tags.any { it.id == id } }
            ?: tags.firstOrNull()?.id

        // Sync the backing flow when auto-selection kicks in
        if (validSelectedTagId != deviceState.selectedTagId) {
            selectedTagIdFlow.value = validSelectedTagId
            
            // Fetch route when auto-selection kicks in
            if (validSelectedTagId != null) {
                val tag = tags.find { it.id == validSelectedTagId }
                if (tag != null) {
                    fetchRoute(phoneState.phonePosition, tag.position)
                }
            }
        }

        MapUiState(
            devices = devices,
            isSimulationRunning = deviceState.isRunning,
            selectedTagId = validSelectedTagId,
            bounds = MapProjection.safeComputeBoundsForDevices(devices),
            phonePosition = phoneState.phonePosition,
            phoneAzimuthDegrees = GeoMath.normalizeDegrees(phoneState.phoneAzimuthDegrees),
            isPhoneGpsLocation = phoneState.isPhoneGpsLocation,
            tagTrails = phoneState.tagTrails,
            geofences = geofences,
            dataSourceMode = remoteState.dataSourceMode,
            isRemoteLoading = remoteState.isRemoteLoading,
            remoteStatusMessage = remoteState.remoteStatusMessage,
            isPositionLoggingEnabled = remoteState.isPositionLoggingEnabled,
            lastPositionLogStatus = remoteState.lastPositionLogStatus,
            lastGeofenceEventStatus = remoteState.lastGeofenceEventStatus,
            tagMovementSpeed = deviceState.tagMovementSpeed,
            routeToSelectedTag = routeState.routeToSelectedTag,
            isRouteLoading = routeState.isRouteLoading,
            routeError = routeState.routeError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MapUiState()
    )

    // ── Data Source Mode Controls ────────────────────────────────────────

    fun switchToLocal() {
        if (dataSourceModeFlow.value == DataSourceMode.LOCAL) return
        simulationEngine.stop()
        
        activeRepository = localRepository
        activeGeofenceRepository = localGeofenceRepository
        
        simulationEngine = UwbSimulationEngine(activeRepository, viewModelScope)
        
        startCollectingRepository(activeRepository)
        startCollectingGeofenceRepository(activeGeofenceRepository)
        startCollectingSimulation(simulationEngine)
        
        dataSourceModeFlow.value = DataSourceMode.LOCAL
        isRemoteLoadingFlow.value = false
        remoteStatusMessageFlow.value = "Using local repository"
        clearTrails()
        refreshSelectedRouteIfPossible()
        syncSimulationToSpeed()
    }

    fun switchToSupabase() {
        if (dataSourceModeFlow.value == DataSourceMode.SUPABASE) return

        viewModelScope.launch {
            isRemoteLoadingFlow.value = true
            remoteStatusMessageFlow.value = "Connecting to Supabase..."
            simulationEngine.stop()

            try {
                remoteRepository.refreshDevices()
                remoteGeofenceRepository.refreshGeofences()

                activeRepository = remoteRepository
                activeGeofenceRepository = remoteGeofenceRepository
                
                simulationEngine = UwbSimulationEngine(activeRepository, viewModelScope)
                
                startCollectingRepository(activeRepository)
                startCollectingGeofenceRepository(activeGeofenceRepository)
                startCollectingSimulation(simulationEngine)

                dataSourceModeFlow.value = DataSourceMode.SUPABASE
                lastPositionLogAtMillis = System.currentTimeMillis() // Reset timer on switch
                remoteStatusMessageFlow.value = "Supabase connected. Devices: ${remoteRepository.getCurrentDevices().size}"
                clearTrails()
                refreshSelectedRouteIfPossible()
                syncSimulationToSpeed()
            } catch (e: Exception) {
                remoteStatusMessageFlow.value = "Supabase failed: ${e.message ?: e::class.simpleName}"
            } finally {
                isRemoteLoadingFlow.value = false
            }
        }
    }

    fun switchToApiMongodb() {
        if (dataSourceModeFlow.value == DataSourceMode.API_MONGODB) return

        viewModelScope.launch {
            isRemoteLoadingFlow.value = true
            remoteStatusMessageFlow.value = "Connecting to MongoDB API..."
            simulationEngine.stop()

            try {
                apiRepository.refreshDevices()
                apiGeofenceRepository.refreshGeofences()

                activeRepository = apiRepository
                activeGeofenceRepository = apiGeofenceRepository
                
                simulationEngine = UwbSimulationEngine(activeRepository, viewModelScope)
                
                startCollectingRepository(activeRepository)
                startCollectingGeofenceRepository(activeGeofenceRepository)
                startCollectingSimulation(simulationEngine)

                dataSourceModeFlow.value = DataSourceMode.API_MONGODB
                lastPositionLogAtMillis = System.currentTimeMillis() // Reset timer on switch
                remoteStatusMessageFlow.value = "MongoDB API connected. Devices: ${apiRepository.getCurrentDevices().size}"
                clearTrails()
                refreshSelectedRouteIfPossible()
                syncSimulationToSpeed()
            } catch (e: Exception) {
                remoteStatusMessageFlow.value = "MongoDB API failed: ${e.message ?: e::class.simpleName}"
            } finally {
                isRemoteLoadingFlow.value = false
            }
        }
    }

    fun toggleDataSourceMode() {
        when (dataSourceModeFlow.value) {
            DataSourceMode.LOCAL -> switchToApiMongodb()
            DataSourceMode.SUPABASE -> switchToApiMongodb()
            DataSourceMode.API_MONGODB -> switchToLocal()
        }
    }

    fun togglePositionLogging() {
        isPositionLoggingEnabledFlow.value = !isPositionLoggingEnabledFlow.value
        lastPositionLogStatusFlow.value = if (isPositionLoggingEnabledFlow.value) {
            "Position logging enabled"
        } else {
            "Position logging disabled"
        }
    }

    // ── Simulation controls ──────────────────────────────────────────────

    /** Starts the random-walk simulation loop. */
    fun startSimulation() {
        val speed = tagMovementSpeedFlow.value.coerceIn(0, 5)
        if (speed <= 0) {
            stopSimulation()
            return
        }

        simulationEngine.stop()
        simulationEngine.start(
            intervalMillis = AUTO_SIMULATION_INTERVAL_MILLIS,
            maxStepMeters = BASE_SIMULATION_STEP_METERS * speed
        )
    }

    /** Stops the simulation loop. */
    fun stopSimulation() {
        simulationEngine.stop()
    }

    /** Toggles the simulation between running and stopped. */
    fun toggleSimulation() {
        setTagMovementSpeed(if (tagMovementSpeedFlow.value == 0) 1 else 0)
    }

    /** Sets the tag movement speed used by auto-simulation. */
    fun setTagMovementSpeed(speed: Int) {
        val clamped = speed.coerceIn(0, 5)
        if (tagMovementSpeedFlow.value == clamped) return

        tagMovementSpeedFlow.value = clamped
        syncSimulationToSpeed()
    }

    /** Stops the simulation and resets all devices to defaults. */
    fun resetSimulation() {
        viewModelScope.launch {
            if (dataSourceModeFlow.value == DataSourceMode.SUPABASE) {
                resetSupabaseDemoPositions()
            } else {
                simulationEngine.reset()
                tagTrailsFlow.value = emptyMap()
                syncSimulationToSpeed()
            }
        }
    }

    private suspend fun resetSupabaseDemoPositions() {
        simulationEngine.stop()

        val devices = activeRepository.getCurrentDevices()
        val tags = devices.filter { it.isTag }

        if (tags.isEmpty()) {
            remoteStatusMessageFlow.value = "Demo reset skipped: no tags found"
            return
        }

        val restrictedTag = tags.firstOrNull { it.id == "tag-t1" } ?: tags.getOrNull(0)
        val safeTag = tags.firstOrNull { it.id == "tag-t2" } ?: tags.getOrNull(1)

        val restrictedPoint = GeoPoint(latitude = 21.036650, longitude = 105.834900)
        val safePoint = GeoPoint(latitude = 21.036850, longitude = 105.834650)

        try {
            var didLog = false
            var totalEvents = 0

            restrictedTag?.let { tag ->
                val updated = tag.withPosition(restrictedPoint)
                activeRepository.updateDevice(updated)
                if (isPositionLoggingEnabledFlow.value) {
                    positionLogRepository.insertDevicePosition(
                        deviceId = updated.id,
                        position = updated.position,
                        source = "DEMO_RESET"
                    )
                    totalEvents += geofenceEventRepository.evaluateForDevice(updated.id)
                    didLog = true
                }
            }

            safeTag?.takeIf { it.id != restrictedTag?.id }?.let { tag ->
                val updated = tag.withPosition(safePoint)
                activeRepository.updateDevice(updated)
                if (isPositionLoggingEnabledFlow.value) {
                    positionLogRepository.insertDevicePosition(
                        deviceId = updated.id,
                        position = updated.position,
                        source = "DEMO_RESET"
                    )
                    totalEvents += geofenceEventRepository.evaluateForDevice(updated.id)
                    didLog = true
                }
            }

            tagTrailsFlow.value = emptyMap()
            lastPositionLogAtMillis = System.currentTimeMillis()
            remoteStatusMessageFlow.value = "Demo reset: tags moved into Safe/Restricted zones"

            if (isPositionLoggingEnabledFlow.value) {
                lastPositionLogStatusFlow.value = if (didLog) {
                    "Demo reset positions logged"
                } else {
                    "Demo reset: no tags to log"
                }
                lastGeofenceEventStatusFlow.value = if (didLog) {
                    "Demo reset evaluated geofence events ($totalEvents)"
                } else {
                    "Demo reset: no tags to evaluate"
                }
            } else {
                lastPositionLogStatusFlow.value = "Demo reset: logging disabled"
                lastGeofenceEventStatusFlow.value = "Demo reset: logging disabled"
            }

            syncSimulationToSpeed()
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = "Demo reset failed: ${e.message ?: e::class.simpleName}"
            remoteStatusMessageFlow.value = errorMsg
            lastPositionLogStatusFlow.value = errorMsg
            lastGeofenceEventStatusFlow.value = errorMsg
        }
    }

    /**
     * Instantly moves Tag T1 (or first tag) inside or outside the restricted zone
     * to trigger geofence events for demo purposes.
     */
    fun triggerDemoGeofenceEvent() {
        viewModelScope.launch {
            try {
                // Stop simulation to prevent immediate movement away from demo point
                simulationEngine.stop()

                val devices = devicesBridgeFlow.value
                val tags = devices.filter { it.isTag }
                if (tags.isEmpty()) {
                    remoteStatusMessageFlow.value = "Demo event failed: no tags found"
                    return@launch
                }

                // Prefer tag-t1, fallback to first tag
                val tag = tags.firstOrNull { it.id == "tag-t1" } ?: tags.first()
                
                val restrictedGeofences = geofencesBridgeFlow.value.filter { it.isRestricted }
                if (restrictedGeofences.isEmpty()) {
                    remoteStatusMessageFlow.value = "Demo event failed: no restricted zone found"
                    return@launch
                }
                
                val restrictedZone = restrictedGeofences.first()
                
                // Determine if we should move IN or OUT
                val isCurrentlyInside = GeofenceMath.containsPoint(tag.position, restrictedZone)
                
                val insideRestricted = GeoPoint(latitude = 21.036650, longitude = 105.834900)
                val outsideRestricted = GeoPoint(latitude = 21.036600, longitude = 105.834900)
                
                val targetPosition = if (isCurrentlyInside) outsideRestricted else insideRestricted
                val eventType = if (isCurrentlyInside) "OUTSIDE" else "INSIDE"
                
                // Update repository
                val updatedDevice = tag.withPosition(targetPosition)
                activeRepository.updateDevice(updatedDevice)
                
                // Append to trail so movement is visible
                val currentTrails = tagTrailsFlow.value.toMutableMap()
                val oldTrail = currentTrails[updatedDevice.id].orEmpty()
                currentTrails[updatedDevice.id] = (oldTrail + updatedDevice.position).takeLast(MAX_TRAIL_POINTS)
                tagTrailsFlow.value = currentTrails

                if (dataSourceModeFlow.value == DataSourceMode.SUPABASE) {
                    // Position logging
                    if (isPositionLoggingEnabledFlow.value) {
                        positionLogRepository.insertDevicePosition(
                            deviceId = updatedDevice.id,
                            position = updatedDevice.position,
                            source = "DEMO_EVENT"
                        )
                        lastPositionLogStatusFlow.value = "Demo event position logged"
                    }
                    
                    // Force geofence evaluation
                    val eventsCreated = geofenceEventRepository.evaluateForDevice(updatedDevice.id)
                    lastGeofenceEventStatusFlow.value = "Demo event evaluated ($eventsCreated events)"
                    
                    remoteStatusMessageFlow.value = "Demo event: Tag ${updatedDevice.name} moved $eventType Restricted Zone"
                } else if (dataSourceModeFlow.value == DataSourceMode.API_MONGODB) {
                    remoteStatusMessageFlow.value = "Demo event: Tag ${updatedDevice.name} position updated via MongoDB API"
                    lastPositionLogStatusFlow.value = "Position updated via MongoDB API"
                    lastGeofenceEventStatusFlow.value = "Geofence evaluated by backend"
                } else {
                    remoteStatusMessageFlow.value = "Demo event: Tag ${updatedDevice.name} moved locally"
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg = "Demo event failed: ${e.message ?: e::class.simpleName}"
                remoteStatusMessageFlow.value = errorMsg
                lastPositionLogStatusFlow.value = errorMsg
                lastGeofenceEventStatusFlow.value = errorMsg
            }
        }
    }

    /** Executes a single simulation step (useful for debugging). */
    fun stepSimulation() {
        viewModelScope.launch { simulationEngine.step() }
    }

    /** Moves all active tags into safe zones for demo/testing. */
    suspend fun moveAllTagsToSafeZone(): String = moveAllTagsToGeofenceZones(
        actionName = "safe_zone",
        targetZones = activeGeofenceRepository.getCurrentGeofences().filter { zone ->
            zone.type == com.example.virtualuwb.domain.model.GeofenceType.SAFE_ZONE ||
                zone.name.contains("safe", ignoreCase = true)
        },
        emptyMessage = "No safe zone available.",
        destinationLabel = "Safe Zone"
    )

    /** Moves all active tags into restricted zones for demo/testing. */
    suspend fun moveAllTagsToRestrictedZone(): String = moveAllTagsToGeofenceZones(
        actionName = "restricted_zone",
        targetZones = activeGeofenceRepository.getCurrentGeofences().filter { zone ->
            zone.type == com.example.virtualuwb.domain.model.GeofenceType.RESTRICTED_ZONE ||
                zone.name.contains("restricted", ignoreCase = true)
        },
        emptyMessage = "No restricted zone available.",
        destinationLabel = "Restricted Zone"
    )

    /** Randomizes all active tags inside the anchor bounding box for demo/testing. */
    suspend fun randomizeTagsInsideAnchors(): String {
        val devices = activeRepository.getCurrentDevices()
        val anchors = devices.filter { it.isAnchor }
        val tags = devices.filter { it.isTag }

        Log.d(TAG, "randomizeTagsInsideAnchors() clicked: anchors=${anchors.size}, tags=${tags.size}, mode=${dataSourceModeFlow.value}")

        if (anchors.size < 4) {
            val message = "At least 4 anchors are required."
            remoteStatusMessageFlow.value = message
            Log.d(TAG, message)
            return message
        }

        if (tags.isEmpty()) {
            val message = "No tags available."
            remoteStatusMessageFlow.value = message
            Log.d(TAG, message)
            return message
        }

        val minLatitude = anchors.minOf { it.position.latitude }
        val maxLatitude = anchors.maxOf { it.position.latitude }
        val minLongitude = anchors.minOf { it.position.longitude }
        val maxLongitude = anchors.maxOf { it.position.longitude }

        val updatedDevices = devices.map { device ->
            if (!device.isTag) device else device.withPosition(
                GeoPoint(
                    latitude = randomDouble(minLatitude, maxLatitude),
                    longitude = randomDouble(minLongitude, maxLongitude)
                )
            )
        }

        activeRepository.replaceAll(updatedDevices)
        refreshActiveRepositories()
        evaluateMovedTags(tags = updatedDevices.filter { it.isTag })

        val message = "Randomized ${tags.size} tags inside anchor area."
        remoteStatusMessageFlow.value = message
        Log.d(TAG, message)
        return message
    }

    /** Backward-compatible wrapper for the previous method name. */
    suspend fun randomizeTagsWithinAnchors(): String = randomizeTagsInsideAnchors()

    // ── Tag selection ────────────────────────────────────────────────────

    /** Selects a tag by its [tagId], or clears the selection when null. */
    fun selectTag(tagId: String?) {
        if (selectedTagIdFlow.value != tagId) {
            selectedTagIdFlow.value = tagId
            tagId?.let { id ->
                val tag = devicesBridgeFlow.value.find { it.id == id && it.isTag }
                if (tag != null) {
                    fetchRoute(phonePositionFlow.value, tag.position)
                }
            }
        }
    }

    /** Selects a tag for map navigation and tracking. */
    fun selectTagForNavigation(tagId: String) {
        selectTag(tagId)
    }

    // ── Trail controls ───────────────────────────────────────────────────

    /** Clears all tag movement trails. */
    fun clearTrails() {
        tagTrailsFlow.value = emptyMap()
    }

    // ── Phone controls (simulated & GPS) ─────────────────────────────────

    fun updatePhonePositionFromGps(latitude: Double, longitude: Double) {
        val newPosition = GeoPoint(latitude, longitude)
        val oldPosition = phonePositionFlow.value
        phonePositionFlow.value = newPosition
        isPhoneGpsLocationFlow.value = true
        
        if (GeoMath.haversineDistanceMeters(oldPosition, newPosition) > 10.0) {
            val tagId = selectedTagIdFlow.value
            if (tagId != null) {
                val tag = devicesBridgeFlow.value.find { it.id == tagId && it.isTag }
                if (tag != null) {
                    fetchRoute(newPosition, tag.position)
                }
            }
        }
    }

    /** Rotates the simulated phone heading 15° counter-clockwise. */
    fun rotatePhoneLeft() {
        phoneAzimuthFlow.value = GeoMath.normalizeDegrees(phoneAzimuthFlow.value - 15.0)
    }

    /** Rotates the simulated phone heading 15° clockwise. */
    fun rotatePhoneRight() {
        phoneAzimuthFlow.value = GeoMath.normalizeDegrees(phoneAzimuthFlow.value + 15.0)
    }

    /** Resets the simulated phone heading to 0° (North). */
    fun resetPhoneAzimuth() {
        phoneAzimuthFlow.value = 0.0
    }

    /** Moves the simulated phone 0.5 m North. */
    fun movePhoneNorth() {
        movePhone(deltaNorthMeters = 0.5, deltaEastMeters = 0.0)
    }

    /** Moves the simulated phone 0.5 m South. */
    fun movePhoneSouth() {
        movePhone(deltaNorthMeters = -0.5, deltaEastMeters = 0.0)
    }

    /** Moves the simulated phone 0.5 m East. */
    fun movePhoneEast() {
        movePhone(deltaNorthMeters = 0.0, deltaEastMeters = 0.5)
    }

    /** Moves the simulated phone 0.5 m West. */
    fun movePhoneWest() {
        movePhone(deltaNorthMeters = 0.0, deltaEastMeters = -0.5)
    }

    /**
     * Moves the simulated phone by the given offsets (in metres),
     * clamping the result to the current device bounding box.
     */
    private fun movePhone(deltaNorthMeters: Double, deltaEastMeters: Double) {
        isPhoneGpsLocationFlow.value = false // Revert to simulated
        val moved = GeoMath.offsetByApproxMeters(
            origin = phonePositionFlow.value,
            deltaNorthMeters = deltaNorthMeters,
            deltaEastMeters = deltaEastMeters
        )

        val devices = activeRepository.getCurrentDevices()
        val bounds = MapProjection.safeComputeBoundsForDevices(devices)

        phonePositionFlow.value = GeoMath.clampToBounds(
            point = moved,
            minLatitude = bounds.minLatitude,
            maxLatitude = bounds.maxLatitude,
            minLongitude = bounds.minLongitude,
            maxLongitude = bounds.maxLongitude
        )
    }

    private fun syncSimulationToSpeed() {
        if (tagMovementSpeedFlow.value <= 0) {
            stopSimulation()
        } else {
            startSimulation()
        }
    }

    private suspend fun moveAllTagsToGeofenceZones(
        actionName: String,
        targetZones: List<Geofence>,
        emptyMessage: String,
        destinationLabel: String
    ): String {
        Log.d(TAG, "$actionName clicked: zones=${targetZones.size}, mode=${dataSourceModeFlow.value}")

        if (targetZones.isEmpty()) {
            remoteStatusMessageFlow.value = emptyMessage
            Log.d(TAG, emptyMessage)
            return emptyMessage
        }

        val devices = activeRepository.getCurrentDevices()
        val tags = devices.filter { it.isTag }
        if (tags.isEmpty()) {
            val message = "No tags available."
            remoteStatusMessageFlow.value = message
            Log.d(TAG, message)
            return message
        }

        val shuffledTags = tags.shuffled()
        val shuffledZones = targetZones.shuffled()
        val updatedDevices = devices.toMutableList()

        val baseCount = shuffledTags.size / shuffledZones.size
        val remainder = shuffledTags.size % shuffledZones.size
        var tagIndex = 0

        shuffledZones.forEachIndexed { zoneIndex, zone ->
            val assignmentCount = baseCount + if (zoneIndex < remainder) 1 else 0
            repeat(assignmentCount) {
                if (tagIndex >= shuffledTags.size) return@repeat

                val tag = shuffledTags[tagIndex++]
                val movedTag = tag.withPosition(randomPointInsideGeofence(zone))
                val deviceIndex = updatedDevices.indexOfFirst { it.id == movedTag.id }
                if (deviceIndex >= 0) {
                    updatedDevices[deviceIndex] = movedTag
                }
            }
        }

        activeRepository.replaceAll(updatedDevices)
        refreshActiveRepositories()
        evaluateMovedTags(tags = updatedDevices.filter { it.isTag })

        val message = "Moved ${shuffledTags.size} tags to $destinationLabel"
        remoteStatusMessageFlow.value = message
        Log.d(TAG, message)
        return message
    }

    private suspend fun evaluateMovedTags(tags: List<UwbDevice>) {
        if (dataSourceModeFlow.value != DataSourceMode.SUPABASE) return

        try {
            val totalEvents = tags.sumOf { tag -> geofenceEventRepository.evaluateForDevice(tag.id) }
            lastGeofenceEventStatusFlow.value = if (totalEvents > 0) {
                "Evaluated $totalEvents geofence events"
            } else {
                "No new geofence events"
            }
            Log.d(TAG, lastGeofenceEventStatusFlow.value.orEmpty())
        } catch (e: Exception) {
            val errorMsg = "Geofence evaluation failed: ${e.message ?: e::class.simpleName}"
            lastGeofenceEventStatusFlow.value = errorMsg
            Log.d(TAG, errorMsg)
        }
    }

    private fun randomPointInsideGeofence(geofence: Geofence): GeoPoint {
        val bounds = MapProjection.computeBounds(geofence.vertices)

        repeat(50) {
            val candidate = GeoPoint(
                latitude = randomDouble(bounds.minLatitude, bounds.maxLatitude),
                longitude = randomDouble(bounds.minLongitude, bounds.maxLongitude)
            )
            if (GeofenceMath.containsPoint(candidate, geofence)) {
                return candidate
            }
        }

        return bounds.center
    }

    private fun randomDouble(min: Double, max: Double): Double {
        return if (min >= max) min else Random.nextDouble(from = min, until = max)
    }

    private suspend fun refreshActiveRepositories() {
        when (val repo = activeRepository) {
            is SupabaseUwbRepository -> repo.refreshDevices()
            is ApiUwbRepository -> repo.refreshDevices()
            is FakeUwbRepository -> Unit
            else -> Unit
        }

        when (val repo = activeGeofenceRepository) {
            is SupabaseGeofenceRepository -> repo.refreshGeofences()
            is ApiGeofenceRepository -> repo.refreshGeofences()
            is FakeGeofenceRepository -> Unit
            else -> Unit
        }
    }


    // ── Device CRUD ──────────────────────────────────────────────────────

    /** Adds (or replaces) a device. */
    fun addDevice(device: UwbDevice) {
        viewModelScope.launch { activeRepository.addDevice(device) }
    }

    /** Updates an existing device, or adds it if not found. */
    fun updateDevice(device: UwbDevice) {
        viewModelScope.launch {
            try {
                activeRepository.updateDevice(device)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update device position: ${e.message}", e)
                if (dataSourceModeFlow.value == DataSourceMode.API_MONGODB) {
                    remoteStatusMessageFlow.value = "Update failed: ${e.message}"
                    lastPositionLogStatusFlow.value = "Update failed: ${e.message}"
                }
            }
        }
    }

    /** Deletes a device by its [deviceId]. */
    fun deleteDevice(deviceId: String) {
        viewModelScope.launch { activeRepository.deleteDevice(deviceId) }
    }

    /** Resets the device list to defaults (without affecting simulation state). */
    fun resetDevices() {
        viewModelScope.launch {
            activeRepository.resetToDefault()
            tagTrailsFlow.value = emptyMap()
        }
    }

    // ── Geofence CRUD ────────────────────────────────────────────────────────

    fun addGeofence(geofence: Geofence) {
        viewModelScope.launch {
            try {
                activeGeofenceRepository.addGeofence(geofence)
                remoteStatusMessageFlow.value = "Geofence added: ${geofence.name}"
            } catch (e: Exception) {
                e.printStackTrace()
                remoteStatusMessageFlow.value = "Add geofence failed: ${e.message ?: e::class.simpleName}"
            }
        }
    }

    fun updateGeofence(geofence: Geofence) {
        viewModelScope.launch {
            try {
                activeGeofenceRepository.updateGeofence(geofence)
                remoteStatusMessageFlow.value = "Geofence updated: ${geofence.name}"
            } catch (e: Exception) {
                e.printStackTrace()
                remoteStatusMessageFlow.value = "Update geofence failed: ${e.message ?: e::class.simpleName}"
            }
        }
    }

    fun deleteGeofence(geofenceId: String) {
        viewModelScope.launch {
            try {
                activeGeofenceRepository.deleteGeofence(geofenceId)
                remoteStatusMessageFlow.value = "Geofence deleted"
            } catch (e: Exception) {
                e.printStackTrace()
                remoteStatusMessageFlow.value = "Delete geofence failed: ${e.message ?: e::class.simpleName}"
            }
        }
    }

    fun resetGeofences() {
        viewModelScope.launch {
            try {
                activeGeofenceRepository.resetToDefault()
                remoteStatusMessageFlow.value = "Geofences reset/refreshed"
            } catch (e: Exception) {
                e.printStackTrace()
                remoteStatusMessageFlow.value = "Reset geofences failed: ${e.message ?: e::class.simpleName}"
            }
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onCleared() {
        simulationEngine.stop()
        repositoryCollectJob?.cancel()
        geofenceCollectJob?.cancel()
        simulationRunningCollectJob?.cancel()
        super.onCleared()
    }
    // ── Routes API ───────────────────────────────────────────────────────

    private fun shouldFallbackToSupabaseRoute(result: RouteResult): Boolean {
        if (result.success) return false
        val err = result.error ?: return false
        val fallbackKeywords = listOf(
            "GOOGLE_ROUTES_FORBIDDEN",
            "403",
            "forbidden",
            "Backend is not running",
            "connection failed",
            "Route backend unavailable",
            "500",
            "502",
            "503",
            "504"
        )
        return fallbackKeywords.any { keyword -> err.contains(keyword, ignoreCase = true) }
    }

    fun fetchRoute(origin: GeoPoint, destination: GeoPoint) {
        // No-op: Route feature is disabled to avoid Google Routes 403 errors.
        routeErrorFlow.value = null
        routeToSelectedTagFlow.value = null
    }

    private fun resolveGoogleRoutesRepository(): GoogleRoutesRepository {
        return when (dataSourceModeFlow.value) {
            DataSourceMode.API_MONGODB -> apiGoogleRoutesRepository
            DataSourceMode.LOCAL, DataSourceMode.SUPABASE -> supabaseGoogleRoutesRepository
        }
    }

    private fun refreshSelectedRouteIfPossible() {
        val tagId = selectedTagIdFlow.value ?: return
        val tag = devicesBridgeFlow.value.find { it.id == tagId && it.isTag } ?: return
        fetchRoute(phonePositionFlow.value, tag.position)
    }
}
