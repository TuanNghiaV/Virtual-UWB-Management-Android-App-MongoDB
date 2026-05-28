package com.example.virtualuwb.presentation.screen.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.GeofenceType
import com.example.virtualuwb.domain.model.UwbDevice
import com.example.virtualuwb.domain.model.UwbRole
import com.example.virtualuwb.presentation.viewmodel.MapUiState
import com.example.virtualuwb.utils.GeoMath
import com.google.android.gms.maps.CameraUpdateFactory
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

private sealed class MapItemInfo {
    data class Tag(val device: UwbDevice) : MapItemInfo()
    data class Anchor(val device: UwbDevice) : MapItemInfo()
    data object Phone : MapItemInfo()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleIndoorMapScreen(
    uiState: MapUiState,
    hasLocationPermission: Boolean = false,
    modifier: Modifier = Modifier,
    onSelectTagForNavigation: (String) -> Unit = {},
    onFetchRoute: () -> Unit = {}
) {
    val hanoiLatLng = LatLng(21.036784, 105.834711)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(hanoiLatLng, 19f)
    }
    val scope = rememberCoroutineScope()
    var isSatelliteMode by remember { mutableStateOf(false) }
    var sheetVisible by remember { mutableStateOf(false) }
    var selectedMapItem by remember { mutableStateOf<MapItemInfo?>(null) }

    // 1. Camera bounds calculation
    LaunchedEffect(uiState.devices, uiState.geofences) {
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
                // padding keeps markers away from edges & bottom nav
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 200))
            } catch (e: Exception) {
                // Safe ignore: Map layout might not be ready yet
            }
        } else {
            try {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(hanoiLatLng, 19f))
            } catch (e: Exception) {
                // Safe ignore: Map layout might not be ready yet
            }
        }
    }

    val phoneLatLng = LatLng(uiState.phonePosition.latitude, uiState.phonePosition.longitude)
    val selectedTag = uiState.selectedTag
    val displayTag = selectedTag ?: uiState.tags.firstOrNull()
    val displayTagId = displayTag?.id
    val displayTagLatLng = displayTag?.let { LatLng(it.latitude, it.longitude) }
    val distanceMeters = displayTag?.let { GeoMath.haversineDistanceMeters(uiState.phonePosition, it.position) }
    val bearingDegrees = displayTag?.let { GeoMath.initialBearingDegrees(uiState.phonePosition, it.position) }
    val directionLabel = bearingDegrees?.let { bearingToDirection(it) }
    var hasSeenInitialSelection by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTag?.id) {
        val tag = selectedTag ?: return@LaunchedEffect
        if (!hasSeenInitialSelection) {
            hasSeenInitialSelection = true
            return@LaunchedEffect
        }
        val zoom = maxOf(cameraPositionState.position.zoom, 19f)
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(tag.latitude, tag.longitude),
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
                compassEnabled = true,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false
            )
        ) {
            // 2. Render Geofences (Polygons)
            uiState.geofences.forEach { geofence ->
                val polygonPoints = geofence.vertices.map { LatLng(it.latitude, it.longitude) }

                Polygon(
                    points = polygonPoints,
                    strokeColor = geofenceStrokeColor(geofence.type),
                    fillColor = geofenceFillColor(geofence.type),
                    strokeWidth = 5f
                )
            }

            // 3. Render Anchors & Tags with overlap mitigation
            val allDevices = uiState.devices
            allDevices.forEach { device ->
                val position = applyDisplayOffsetIfOverlapping(device, allDevices)
                val colorHue = if (device.id == displayTagId && device.isTag) {
                    BitmapDescriptorFactory.HUE_RED
                } else {
                    deviceMarkerColor(device.role)
                }

                Marker(
                    state = MarkerState(position = position),
                    title = device.name,
                    // Disable default InfoWindow
                    onClick = {
                        selectedMapItem = if (device.isTag) {
                            MapItemInfo.Tag(device)
                        } else {
                            MapItemInfo.Anchor(device)
                        }
                        true
                    },
                    icon = BitmapDescriptorFactory.defaultMarker(colorHue)
                )
            }

            // 4. Render Phone position (Simulated or GPS)
            Marker(
                state = MarkerState(position = phoneLatLng),
                title = "Phone",
                // Disable default InfoWindow
                onClick = {
                    selectedMapItem = MapItemInfo.Phone
                    true
                },
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
            )

            // 5. Guidance line from phone to the display target
            val route = uiState.routeToSelectedTag
            if (route != null && route.success && route.encodedPolyline != null) {
                val decodedPoints = decodePolyline(route.encodedPolyline)
                if (decodedPoints.isNotEmpty()) {
                    Polyline(
                        points = decodedPoints,
                        color = Color(0xFF2962FF),
                        width = 8f
                    )
                }
            } else if (displayTagLatLng != null) {
                Polyline(
                    points = listOf(phoneLatLng, displayTagLatLng),
                    color = Color(0xFF2962FF),
                    width = 6f
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TargetTrackingCard(
                displayTag = displayTag,
                distanceMeters = distanceMeters,
                directionLabel = directionLabel,
                routeResult = uiState.routeToSelectedTag,
                isRouteLoading = uiState.isRouteLoading,
                routeError = uiState.routeError,
                onFetchRouteClick = onFetchRoute,
                onClick = { sheetVisible = true }
            )

            MapQuickControlsRow(
                isSatelliteMode = isSatelliteMode,
                onZoomIn = {
                    val currentZoom = cameraPositionState.position.zoom
                    val newZoom = (currentZoom + 1f).coerceIn(15f, 22f)
                    scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomTo(newZoom)) }
                },
                onZoomOut = {
                    val currentZoom = cameraPositionState.position.zoom
                    val newZoom = (currentZoom - 1f).coerceIn(15f, 22f)
                    scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomTo(newZoom)) }
                },
                onToggleMapType = { isSatelliteMode = !isSatelliteMode },
                onLocatePhone = {
                    val currentZoom = cameraPositionState.position.zoom
                    scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(phoneLatLng, currentZoom)) }
                },
                onOpenTagSelector = { sheetVisible = true }
            )
        }

        // Custom info card overlay
        selectedMapItem?.let { item ->
            MapItemInfoCard(
                item = item,
                uiState = uiState,
                onClose = { selectedMapItem = null },
                onNavigateToTag = { tagId ->
                    onSelectTagForNavigation(tagId)
                    selectedMapItem = null
                },
                onFetchRouteClick = onFetchRoute,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 112.dp)
            )
        }

        if (sheetVisible) {
            TagSelectorBottomSheet(
                tags = uiState.tags,
                selectedTagId = displayTagId,
                phonePosition = uiState.phonePosition,
                onDismiss = { sheetVisible = false },
                onSelectTag = { tag ->
                    onSelectTagForNavigation(tag.id)
                    sheetVisible = false
                    val zoom = maxOf(cameraPositionState.position.zoom, 19f)
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(tag.latitude, tag.longitude),
                                zoom
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun MapItemInfoCard(
    item: MapItemInfo,
    uiState: MapUiState,
    onClose: () -> Unit,
    onNavigateToTag: (String) -> Unit,
    onFetchRouteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (item) {
                        is MapItemInfo.Tag -> item.device.name
                        is MapItemInfo.Anchor -> item.device.name
                        MapItemInfo.Phone -> "Your Phone"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            val positionText = when (item) {
                is MapItemInfo.Tag -> "Lat: ${formatCoord(item.device.latitude)}, Lng: ${formatCoord(item.device.longitude)}"
                is MapItemInfo.Anchor -> "Lat: ${formatCoord(item.device.latitude)}, Lng: ${formatCoord(item.device.longitude)}"
                MapItemInfo.Phone -> "Lat: ${formatCoord(uiState.phonePosition.latitude)}, Lng: ${formatCoord(uiState.phonePosition.longitude)}"
            }
            Text(text = positionText, style = MaterialTheme.typography.bodyMedium)
            
            val distanceText = when (item) {
                is MapItemInfo.Tag -> {
                    val dist = GeoMath.haversineDistanceMeters(uiState.phonePosition, item.device.position)
                    "Distance: ${String.format(java.util.Locale.US, "%.1f", dist)} m"
                }
                is MapItemInfo.Anchor -> {
                    val dist = GeoMath.haversineDistanceMeters(uiState.phonePosition, item.device.position)
                    "Distance: ${String.format(java.util.Locale.US, "%.1f", dist)} m"
                }
                MapItemInfo.Phone -> null
            }
            
            if (distanceText != null) {
                Text(text = distanceText, style = MaterialTheme.typography.bodyMedium)
            }
            
            val roleText = when (item) {
                is MapItemInfo.Tag -> "Role: Tag"
                is MapItemInfo.Anchor -> "Role: Anchor"
                MapItemInfo.Phone -> "Role: Phone"
            }
            Text(text = roleText, style = MaterialTheme.typography.bodyMedium)
            
            if (item is MapItemInfo.Tag) {
                Button(
                    onClick = {
                        onNavigateToTag(item.device.id)
                        onFetchRouteClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Guide me")
                }
            }
        }
    }
}

@Composable
private fun TargetTrackingCard(
    displayTag: UwbDevice?,
    distanceMeters: Double?,
    directionLabel: String?,
    routeResult: com.example.virtualuwb.domain.model.RouteResult?,
    isRouteLoading: Boolean,
    routeError: String?,
    onFetchRouteClick: () -> Unit,
    onClick: () -> Unit
) {
    val statusLabel = when {
        displayTag == null -> "Select"
        distanceMeters != null && distanceMeters <= 2.0 -> "Reached"
        distanceMeters != null && distanceMeters <= 5.0 -> "Nearby"
        else -> "Tracking"
    }

    val subtitle = when {
        displayTag == null -> "Select target"
        isRouteLoading -> "Calculating route..."
        routeError != null -> {
            val shortError = if (routeError.length > 25) routeError.take(22) + "..." else routeError
            val directStr = distanceMeters?.let { String.format(java.util.Locale.US, "%.1f m", it) } ?: ""
            "Route Err: $shortError • Direct: $directStr"
        }
        routeResult != null && routeResult.success -> {
            val dist = routeResult.distanceMeters ?: 0
            val dur = routeResult.duration ?: ""
            "Route: $dist m · $dur (Google Routes)"
        }
        distanceMeters != null && directionLabel != null ->
            String.format(java.util.Locale.US, "%.1f m · %s (Direct guidance)", distanceMeters, directionLabel)
        distanceMeters != null -> String.format(java.util.Locale.US, "%.1f m (Direct guidance)", distanceMeters)
        else -> "Tracking"
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTag?.name ?: "Select target",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (routeError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }

            if (displayTag != null) {
                Button(
                    onClick = onFetchRouteClick,
                    enabled = !isRouteLoading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(text = "Route", fontSize = 12.sp)
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = statusLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MapQuickControlsRow(
    isSatelliteMode: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleMapType: () -> Unit,
    onLocatePhone: () -> Unit,
    onOpenTagSelector: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallMapControlButton(text = "+", onClick = onZoomIn)
        SmallMapControlButton(text = "−", onClick = onZoomOut)
        SmallMapControlButton(
            text = if (isSatelliteMode) "MAP" else "SAT",
            active = isSatelliteMode,
            onClick = onToggleMapType
        )
        SmallMapControlButton(text = "GPS", onClick = onLocatePhone)
        SmallMapControlButton(text = "Tags", onClick = onOpenTagSelector)
    }
}

@Composable
private fun SmallMapControlButton(
    text: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.primary,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        border = if (!active) BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        ) else null,
        modifier = Modifier.height(42.dp).defaultMinSize(minWidth = when(text) {
            "+", "−" -> 44.dp
            "SAT", "MAP" -> 52.dp
            "GPS" -> 54.dp
            "Tags" -> 64.dp
            else -> 44.dp
        })
    ) {
        Box(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = if (text == "+" || text == "−") 18.sp else 12.sp,
                fontWeight = if (text == "+" || text == "−") FontWeight.Bold else FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSelectorBottomSheet(
    tags: List<UwbDevice>,
    selectedTagId: String?,
    phonePosition: GeoPoint,
    onDismiss: () -> Unit,
    onSelectTag: (UwbDevice) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            tags.forEach { tag ->
                val distance = GeoMath.haversineDistanceMeters(phonePosition, tag.position)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTag(tag) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${tag.id} • ${String.format(java.util.Locale.US, "%.1f m", distance)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                    if (tag.id == selectedTagId) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun bearingToDirection(bearing: Double): String {
    val directions = arrayOf(
        "North",
        "Northeast",
        "East",
        "Southeast",
        "South",
        "Southwest",
        "West",
        "Northwest"
    )
    val normalized = ((bearing % 360) + 360) % 360
    val index = ((normalized + 22.5) / 45.0).toInt() % directions.size
    return directions[index]
}

// ── Helpers ─────────────────────────────────────────────────────────────

private fun geofenceStrokeColor(type: GeofenceType): Color {
    return when (type) {
        GeofenceType.RESTRICTED_ZONE -> Color(0xFFD32F2F)
        GeofenceType.SAFE_ZONE -> Color(0xFF388E3C)
        GeofenceType.ROOM -> Color.Gray
    }
}

private fun geofenceFillColor(type: GeofenceType): Color {
    return geofenceStrokeColor(type).copy(alpha = 0.15f)
}

private fun deviceMarkerColor(role: UwbRole): Float {
    return when (role) {
        // Azure for Anchors, Orange for Tags
        UwbRole.ANCHOR -> BitmapDescriptorFactory.HUE_AZURE
        UwbRole.TAG -> BitmapDescriptorFactory.HUE_ORANGE
    }
}

private fun formatCoord(value: Double): String = String.format(java.util.Locale.US, "%.5f", value)

private fun isVeryClose(p1: GeoPoint, p2: GeoPoint): Boolean {
    // Roughly equal within ~0.5 meters
    return kotlin.math.abs(p1.latitude - p2.latitude) < 0.000005 &&
           kotlin.math.abs(p1.longitude - p2.longitude) < 0.000005
}

/**
 * Ensures tags don't perfectly stack on top of each other.
 * If multiple devices share the same coordinate, they are spaced out in a tiny circle.
 */
private fun applyDisplayOffsetIfOverlapping(
    device: UwbDevice,
    allDevices: List<UwbDevice>
): LatLng {
    val original = device.position
    val duplicates = allDevices.filter { isVeryClose(it.position, original) }
    
    if (duplicates.size <= 1) {
        return LatLng(original.latitude, original.longitude)
    }
    
    // Sort to make the visual index deterministic (no jitter on re-render)
    val sortedDuplicates = duplicates.sortedBy { it.id }
    val index = sortedDuplicates.indexOfFirst { it.id == device.id }
    
    if (index <= 0) {
        return LatLng(original.latitude, original.longitude)
    }
    
    // Offset in a small circle (~1.5 meters) so pins are independently clickable
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
