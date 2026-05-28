package com.example.virtualuwb.presentation.screen.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.Geofence
import com.example.virtualuwb.domain.model.GeofenceType
import com.example.virtualuwb.domain.model.UwbDevice
import com.example.virtualuwb.domain.model.UwbRole
import com.example.virtualuwb.presentation.viewmodel.MapUiState
import com.example.virtualuwb.utils.GeoMath
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.example.virtualuwb.R


private val PrimaryIndigo = Color(0xFF6366F1)
private val SafeGreen = Color(0xFF10B981)
private val DangerRed = Color(0xFFEF4444)
private val UnknownGray = Color(0xFF94A3B8)
private val GlassSurface = Color.White.copy(alpha = 0.90f)
private val GlassBorder = Color.Black.copy(alpha = 0.08f)

private enum class SafetyStatus {
    SAFE, DANGER, UNKNOWN
}

private fun getTagSafetyStatus(tag: UwbDevice, geofences: List<Geofence>): SafetyStatus {
    val point = tag.position
    val inRestricted = geofences.any { it.type == GeofenceType.RESTRICTED_ZONE && com.example.virtualuwb.utils.GeofenceMath.containsPoint(point, it) }
    if (inRestricted) return SafetyStatus.DANGER
    val inSafe = geofences.any { it.type == GeofenceType.SAFE_ZONE && com.example.virtualuwb.utils.GeofenceMath.containsPoint(point, it) }
    if (inSafe) return SafetyStatus.SAFE
    return SafetyStatus.UNKNOWN
}

private fun getTagSafetyZoneName(tag: UwbDevice, geofences: List<Geofence>): String? {
    val point = tag.position
    val restrictedZone = geofences.firstOrNull { it.type == GeofenceType.RESTRICTED_ZONE && com.example.virtualuwb.utils.GeofenceMath.containsPoint(point, it) }
    if (restrictedZone != null) return restrictedZone.name
    val safeZone = geofences.firstOrNull { it.type == GeofenceType.SAFE_ZONE && com.example.virtualuwb.utils.GeofenceMath.containsPoint(point, it) }
    if (safeZone != null) return safeZone.name
    return null
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleIndoorMapScreen(
    uiState: MapUiState,
    hasLocationPermission: Boolean = false,
    modifier: Modifier = Modifier,
    onSelectTagForNavigation: (String?) -> Unit = {},
    onFetchRoute: () -> Unit = {}
) {
    val hanoiLatLng = LatLng(21.036784, 105.834711)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(hanoiLatLng, 19f)
    }
    val scope = rememberCoroutineScope()
    var isSatelliteMode by remember { mutableStateOf(false) }
    var sheetVisible by remember { mutableStateOf(false) }
    var selectedAnchor by remember { mutableStateOf<UwbDevice?>(null) }

    val context = LocalContext.current
    var markerIcons by remember { mutableStateOf<MapMarkerIcons?>(null) }

    LaunchedEffect(context) {
        try {
            MapsInitializer.initialize(context)
            markerIcons = MapMarkerIcons(
                phone = bitmapDescriptorFromVector(context, R.drawable.ic_marker_phone, 30),
                anchor = bitmapDescriptorFromVector(context, R.drawable.ic_marker_anchor, 30),
                tagSafe = bitmapDescriptorFromVector(context, R.drawable.ic_marker_tag_safe, 36),
                tagDanger = bitmapDescriptorFromVector(context, R.drawable.ic_marker_tag_danger, 36),
                tagSelected = bitmapDescriptorFromVector(context, R.drawable.ic_marker_tag_selected, 46)
            )
        } catch (e: Exception) {
            // Safe fallback
        }
    }



    // Camera bounds calculation (overview)
    fun fitMapOverview() {
        val allLatLngs = mutableListOf<LatLng>()
        uiState.devices.forEach { allLatLngs.add(LatLng(it.latitude, it.longitude)) }
        uiState.geofences.forEach { geo ->
            geo.vertices.forEach { allLatLngs.add(LatLng(it.latitude, it.longitude)) }
        }

        if (allLatLngs.isNotEmpty()) {
            val builder = LatLngBounds.builder()
            allLatLngs.forEach { builder.include(it) }
            val bounds = builder.build()
            try {
                cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 180))
            } catch (e: Exception) {
                // Ignore layout not ready
            }
        }
    }

    LaunchedEffect(uiState.devices, uiState.geofences) {
        fitMapOverview()
    }

    val phoneLatLng = LatLng(uiState.phonePosition.latitude, uiState.phonePosition.longitude)
    val selectedTag = uiState.selectedTag
    val displayTagLatLng = selectedTag?.let { LatLng(it.latitude, it.longitude) }
    val distanceMeters = selectedTag?.let { GeoMath.haversineDistanceMeters(uiState.phonePosition, it.position) }
    val bearingDegrees = selectedTag?.let { GeoMath.initialBearingDegrees(uiState.phonePosition, it.position) }
    val directionLabel = bearingDegrees?.let { bearingToDirection(it) }

    // Focus camera on selected tag when chosen
    LaunchedEffect(selectedTag?.id) {
        val tag = selectedTag ?: return@LaunchedEffect
        val zoom = maxOf(cameraPositionState.position.zoom, 19.5f)
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(tag.latitude, tag.longitude),
                zoom
            )
        )
    }

    // Focus camera on selected anchor when chosen
    LaunchedEffect(selectedAnchor?.id) {
        val anchor = selectedAnchor ?: return@LaunchedEffect
        val zoom = maxOf(cameraPositionState.position.zoom, 19.5f)
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(anchor.latitude, anchor.longitude),
                zoom
            )
        )
    }


    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = if (isSatelliteMode) MapType.HYBRID else MapType.NORMAL,
                isMyLocationEnabled = hasLocationPermission
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = false,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false
            )
        ) {
            // Render Geofences (Polygons)
            uiState.geofences.forEach { geofence ->
                val polygonPoints = geofence.vertices.map { LatLng(it.latitude, it.longitude) }
                Polygon(
                    points = polygonPoints,
                    strokeColor = geofenceStrokeColor(geofence.type),
                    fillColor = geofenceFillColor(geofence.type),
                    strokeWidth = 4f
                )
            }

            // Render Anchors & Tags
            val allDevices = uiState.devices
            allDevices.forEach { device ->
                val position = applyDisplayOffsetIfOverlapping(device, allDevices)
                val isSelected = selectedTag?.id == device.id && device.isTag
                
                val markerIcon = when {
                    device.role == UwbRole.ANCHOR -> markerIcons?.anchor ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
                    isSelected -> markerIcons?.tagSelected ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
                    device.isTag && uiState.restrictedGeofences.any { zone -> com.example.virtualuwb.utils.GeofenceMath.containsPoint(device.position, zone) } -> markerIcons?.tagDanger ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    else -> markerIcons?.tagSafe ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                }

                Marker(
                    state = MarkerState(position = position),
                    title = device.name,
                    onClick = {
                        if (device.isTag) {
                            selectedAnchor = null
                            onSelectTagForNavigation(device.id)
                        } else {
                            onSelectTagForNavigation(null)
                            selectedAnchor = device
                        }
                        true
                    },
                    icon = markerIcon
                )

            }

            // Render Phone Position
            Marker(
                state = MarkerState(position = phoneLatLng),
                title = "Your Phone",
                onClick = { true },
                icon = markerIcons?.phone ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
            )



            // Render Route Polyline
            val route = uiState.routeToSelectedTag
            if (uiState.hasRequestedRoute) {
                if (route != null && route.success && route.encodedPolyline != null) {
                    val decodedPoints = decodePolyline(route.encodedPolyline)
                    if (decodedPoints.isNotEmpty()) {
                        Polyline(
                            points = decodedPoints,
                            color = PrimaryIndigo,
                            width = 8f
                        )
                    }
                } else if (uiState.routeError != null && displayTagLatLng != null) {
                    Polyline(
                        points = listOf(phoneLatLng, displayTagLatLng),
                        color = PrimaryIndigo.copy(alpha = 0.8f),
                        width = 6f
                    )
                }
            }
        }

        // 1. Top status capsule
        MapStatusCapsule(
            selectedTag = selectedTag,
            uiState = uiState,
            distanceMeters = distanceMeters,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
        )

        // 2. Right-side vertical controls
        MapControlStack(
            isSatelliteMode = isSatelliteMode,
            onZoomIn = {
                val newZoom = (cameraPositionState.position.zoom + 1f).coerceIn(15f, 22f)
                scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomTo(newZoom)) }
            },
            onZoomOut = {
                val newZoom = (cameraPositionState.position.zoom - 1f).coerceIn(15f, 22f)
                scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomTo(newZoom)) }
            },
            onToggleMapType = { isSatelliteMode = !isSatelliteMode },
            onRecenterPhone = {
                scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(phoneLatLng, 19.5f)) }
            },
            onOpenTags = { sheetVisible = true },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        )

        // 3. Selected tag bottom card
        AnimatedVisibility(
            visible = selectedTag != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .padding(bottom = 136.dp)
        ) {
            if (selectedTag != null) {
                val nearestAnchor = findNearestAnchor(selectedTag, uiState.devices)
                SelectedTagBottomCard(
                    device = selectedTag,
                    distanceMeters = distanceMeters,
                    directionLabel = directionLabel,
                    routeResult = uiState.routeToSelectedTag,
                    isRouteLoading = uiState.isRouteLoading,
                    routeError = uiState.routeError,
                    nearestAnchor = nearestAnchor,
                    safetyStatus = getTagSafetyStatus(selectedTag, uiState.geofences),
                    onFetchRoute = onFetchRoute,
                    onClose = {
                        onSelectTagForNavigation(null)
                        fitMapOverview()
                    }
                )
            }
        }

        // 4. Selected anchor bottom card
        AnimatedVisibility(
            visible = selectedAnchor != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .padding(bottom = 136.dp)
        ) {
            if (selectedAnchor != null) {
                val nearestTag = findNearestTag(selectedAnchor!!, uiState.devices)
                SelectedAnchorBottomCard(
                    device = selectedAnchor!!,
                    nearestTag = nearestTag,
                    onClose = {
                        selectedAnchor = null
                        fitMapOverview()
                    }
                )
            }
        }

        // Tags Bottom Sheet Selector
        if (sheetVisible) {
            TagSelectorBottomSheet(
                tags = uiState.tags,
                selectedTagId = selectedTag?.id,
                phonePosition = uiState.phonePosition,
                uiState = uiState,
                onDismiss = { sheetVisible = false },
                onSelectTag = { tag ->
                    selectedAnchor = null
                    onSelectTagForNavigation(tag.id)
                    sheetVisible = false
                }
            )
        }
    }
}

@Composable
private fun MapStatusCapsule(
    selectedTag: UwbDevice?,
    uiState: MapUiState,
    distanceMeters: Double?,
    modifier: Modifier = Modifier
) {
    val dangerCount = uiState.tags.count { tag ->
        getTagSafetyStatus(tag, uiState.geofences) == SafetyStatus.DANGER
    }

    val route = uiState.routeToSelectedTag

    val text = when {
        selectedTag == null -> {
            "Live Map · ${uiState.tags.size} Tags · $dangerCount Alert"
        }
        uiState.isRouteLoading -> {
            "Calculating route..."
        }
        uiState.hasRequestedRoute && route != null && route.success -> {
            val dist = route.distanceMeters ?: 0
            val dur = route.duration ?: "..."
            "Route to ${selectedTag.name} · ${dist}m · $dur"
        }
        uiState.hasRequestedRoute && uiState.routeError != null -> {
            "Route unavailable · Direct fallback"
        }
        else -> {
            val safetyStatus = getTagSafetyStatus(selectedTag, uiState.geofences)
            val safety = when (safetyStatus) {
                SafetyStatus.DANGER -> "Danger"
                SafetyStatus.SAFE -> "Safe"
                SafetyStatus.UNKNOWN -> "Unknown"
            }
            val distStr = distanceMeters?.let { String.format(java.util.Locale.US, "%.1fm", it) } ?: "..."
            "${selectedTag.name} · $safety · $distStr"
        }
    }

    val statusColor = when {
        selectedTag == null && dangerCount > 0 -> DangerRed
        selectedTag == null -> SafeGreen
        uiState.isRouteLoading -> PrimaryIndigo
        uiState.hasRequestedRoute && uiState.routeError != null -> DangerRed
        selectedTag != null -> {
            when (getTagSafetyStatus(selectedTag, uiState.geofences)) {
                SafetyStatus.DANGER -> DangerRed
                SafetyStatus.SAFE -> SafeGreen
                SafetyStatus.UNKNOWN -> UnknownGray
            }
        }
        else -> SafeGreen
    }


    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassBorder),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E293B)
            )
        }
    }
}

@Composable
private fun MapControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, GlassBorder),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) PrimaryIndigo else Color(0xFF475569),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MapControlStack(
    isSatelliteMode: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleMapType: () -> Unit,
    onRecenterPhone: () -> Unit,
    onOpenTags: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MapControlButton(
            icon = Icons.Default.MyLocation,
            contentDescription = "Recenter",
            onClick = onRecenterPhone,
            selected = true
        )
        MapControlButton(
            icon = Icons.Default.ZoomIn,
            contentDescription = "Zoom In",
            onClick = onZoomIn
        )
        MapControlButton(
            icon = Icons.Default.ZoomOut,
            contentDescription = "Zoom Out",
            onClick = onZoomOut
        )
        MapControlButton(
            icon = if (isSatelliteMode) Icons.Default.Map else Icons.Default.Layers,
            contentDescription = "Map Type",
            onClick = onToggleMapType,
            selected = isSatelliteMode
        )
        MapControlButton(
            icon = Icons.Default.People,
            contentDescription = "Tags Selector",
            onClick = onOpenTags
        )
    }
}

private data class NearestAnchorInfo(
    val name: String,
    val distanceMeters: Double
)

private fun findNearestAnchor(tag: UwbDevice, devices: List<UwbDevice>): NearestAnchorInfo? {
    val anchors = devices.filter { it.role == UwbRole.ANCHOR }
    if (anchors.isEmpty()) return null
    return anchors.map { anchor ->
        NearestAnchorInfo(
            name = anchor.name,
            distanceMeters = GeoMath.haversineDistanceMeters(tag.position, anchor.position)
        )
    }.minByOrNull { it.distanceMeters }
}

@Composable
private fun SelectedTagBottomCard(
    device: UwbDevice,
    distanceMeters: Double?,
    directionLabel: String?,
    routeResult: com.example.virtualuwb.domain.model.RouteResult?,
    isRouteLoading: Boolean,
    routeError: String?,
    nearestAnchor: NearestAnchorInfo?,
    safetyStatus: SafetyStatus,
    onFetchRoute: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, GlassBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            // Close Button
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SafetyChip(safetyStatus = safetyStatus)
                }

                Text(
                    text = "TAG · ${device.id}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(top = 1.dp)
                )

                if (safetyStatus == SafetyStatus.UNKNOWN) {
                    Text(
                        text = "Outside defined zones",
                        style = MaterialTheme.typography.labelSmall,
                        color = UnknownGray,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                // Nearest Anchor Info
                if (nearestAnchor != null) {
                    Text(
                        text = "Closest anchor · ${nearestAnchor.name} · ${String.format(java.util.Locale.US, "%.1f m", nearestAnchor.distanceMeters)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Info Section
                Spacer(modifier = Modifier.height(10.dp))
                val directStr = if (distanceMeters != null && directionLabel != null) {
                    "${String.format(java.util.Locale.US, "%.1f m", distanceMeters)} · $directionLabel"
                } else {
                    "Unknown"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Direct", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                        Text(directStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                    }

                    if (routeResult != null && routeResult.success) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Google Route", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                            Text(
                                text = "${routeResult.distanceMeters}m · ${routeResult.duration}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryIndigo
                            )
                        }
                    } else if (isRouteLoading) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Google Route", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                            Text("Calculating...", style = MaterialTheme.typography.bodyMedium, color = PrimaryIndigo)
                        }
                    } else if (routeError != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Google Route", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                            Text("Unavailable", style = MaterialTheme.typography.bodyMedium, color = DangerRed)
                        }
                    }
                }

                // Full-width button
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onFetchRoute,
                    enabled = !isRouteLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (routeResult != null) "Update Route" else "Route",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyChip(safetyStatus: SafetyStatus) {
    val label = when (safetyStatus) {
        SafetyStatus.SAFE -> "Safe"
        SafetyStatus.DANGER -> "Danger"
        SafetyStatus.UNKNOWN -> "Unknown"
    }
    val color = when (safetyStatus) {
        SafetyStatus.SAFE -> SafeGreen
        SafetyStatus.DANGER -> DangerRed
        SafetyStatus.UNKNOWN -> UnknownGray
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Text(
            text = label.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}

private data class NearestTagInfo(
    val name: String,
    val distanceMeters: Double
)

private fun findNearestTag(anchor: UwbDevice, devices: List<UwbDevice>): NearestTagInfo? {
    val tags = devices.filter { it.role == UwbRole.TAG }
    if (tags.isEmpty()) return null
    return tags.map { tag ->
        NearestTagInfo(
            name = tag.name,
            distanceMeters = GeoMath.haversineDistanceMeters(anchor.position, tag.position)
        )
    }.minByOrNull { it.distanceMeters }
}

@Composable
private fun SelectedAnchorBottomCard(
    device: UwbDevice,
    nearestTag: NearestTagInfo?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, GlassBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            // Close Button
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                Text(
                    text = "ANCHOR · ${device.id}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(top = 1.dp)
                )

                Text(
                    text = "Fixed infrastructure node",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF334155),
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Coordinates", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                        Text(
                            text = String.format(java.util.Locale.US, "%.6f, %.6f", device.latitude, device.longitude),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                    }

                    if (nearestTag != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Closest Tag", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                            Text(
                                text = "${nearestTag.name} · ${String.format(java.util.Locale.US, "%.1fm", nearestTag.distanceMeters)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryIndigo
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSelectorBottomSheet(
    tags: List<UwbDevice>,
    selectedTagId: String?,
    phonePosition: GeoPoint,
    uiState: MapUiState,
    onDismiss: () -> Unit,
    onSelectTag: (UwbDevice) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GlassSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 20.dp)
        ) {
            Text(
                text = "Select Tag",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            tags.forEach { tag ->
                val distance = GeoMath.haversineDistanceMeters(phonePosition, tag.position)
                val isSelected = tag.id == selectedTagId

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) PrimaryIndigo.copy(alpha = 0.08f) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, PrimaryIndigo.copy(alpha = 0.2f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTag(tag) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "${tag.id} · ${String.format(java.util.Locale.US, "%.1f m", distance)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B)
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = PrimaryIndigo
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun bearingToDirection(bearing: Double): String {
    val directions = arrayOf(
        "N", "NE", "E", "SE", "S", "SW", "W", "NW"
    )
    val normalized = ((bearing % 360) + 360) % 360
    val index = ((normalized + 22.5) / 45.0).toInt() % directions.size
    return directions[index]
}

private fun geofenceStrokeColor(type: GeofenceType): Color {
    return when (type) {
        GeofenceType.RESTRICTED_ZONE -> DangerRed
        GeofenceType.SAFE_ZONE -> SafeGreen
        GeofenceType.ROOM -> Color.Gray
    }
}

private fun geofenceFillColor(type: GeofenceType): Color {
    return geofenceStrokeColor(type).copy(alpha = 0.12f)
}

private fun deviceMarkerColor(role: UwbRole): Float {
    return when (role) {
        UwbRole.ANCHOR -> BitmapDescriptorFactory.HUE_AZURE
        UwbRole.TAG -> BitmapDescriptorFactory.HUE_ORANGE
    }
}

private fun isVeryClose(p1: GeoPoint, p2: GeoPoint): Boolean {
    return kotlin.math.abs(p1.latitude - p2.latitude) < 0.000005 &&
           kotlin.math.abs(p1.longitude - p2.longitude) < 0.000005
}

private fun applyDisplayOffsetIfOverlapping(
    device: UwbDevice,
    allDevices: List<UwbDevice>
): LatLng {
    val original = device.position
    val duplicates = allDevices.filter { isVeryClose(it.position, original) }
    
    if (duplicates.size <= 1) {
        return LatLng(original.latitude, original.longitude)
    }
    
    val sortedDuplicates = duplicates.sortedBy { it.id }
    val index = sortedDuplicates.indexOfFirst { it.id == device.id }
    
    if (index <= 0) {
        return LatLng(original.latitude, original.longitude)
    }
    
    val radius = 0.000015
    val angle = (Math.PI * 2 / duplicates.size) * index
    val latOffset = radius * kotlin.math.cos(angle)
    val lonOffset = radius * kotlin.math.sin(angle)
    
    return LatLng(original.latitude + latOffset, original.longitude + lonOffset)
}

private fun decodePolyline(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0
    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += dlat
        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lng += dlng
        poly.add(LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
    }
    return poly
}

private fun bitmapDescriptorFromVector(
    context: Context,
    vectorResId: Int,
    sizeDp: Int
): BitmapDescriptor? {
    return try {
        val drawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        BitmapDescriptorFactory.fromBitmap(bitmap)
    } catch (e: Exception) {
        null
    }
}

private class MapMarkerIcons(
    val phone: BitmapDescriptor?,
    val anchor: BitmapDescriptor?,
    val tagSafe: BitmapDescriptor?,
    val tagDanger: BitmapDescriptor?,
    val tagSelected: BitmapDescriptor?
)


