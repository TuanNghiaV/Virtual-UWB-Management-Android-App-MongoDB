package com.example.virtualuwb.presentation.screen.findmy

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.Geofence
import com.example.virtualuwb.domain.model.GeofenceType
import com.example.virtualuwb.domain.model.UwbDevice
import com.example.virtualuwb.presentation.viewmodel.MapUiState
import com.example.virtualuwb.utils.GeoMath
import com.example.virtualuwb.utils.GeofenceMath
import java.util.Locale

private val PrimaryColor = Color(0xFF6366F1) // Indigo/Purple
private val LightBorder = Color(0xFFE5E7EB)
private val ScreenBackground = Color(0xFFF9FAFB)
private val AccentColor = Color(0xFF10B981) // Green

private const val PROXIMITY_THRESHOLD = 1.5 // Meters

private fun distanceStatus(distanceMeters: Double): String =
    when {
        distanceMeters < PROXIMITY_THRESHOLD -> "Reached"
        distanceMeters < 3.0 -> "Very Near"
        distanceMeters < 7.0 -> "Near"
        distanceMeters < 15.0 -> "Medium"
        else -> "Far"
    }

private fun statusColor(status: String): Color =
    when (status) {
        "Reached", "Very Near" -> Color(0xFF10B981) // Green
        "Near", "Medium" -> Color(0xFF6366F1) // Indigo
        else -> Color(0xFF9CA3AF) // Gray
    }

@Composable
fun FindMyScreen(
    uiState: MapUiState,
    onSelectTag: (String) -> Unit,
    onRotatePhoneLeft: () -> Unit,
    onRotatePhoneRight: () -> Unit,
    onResetPhoneAzimuth: () -> Unit,
    onMovePhoneNorth: () -> Unit,
    onMovePhoneSouth: () -> Unit,
    onMovePhoneEast: () -> Unit,
    onMovePhoneWest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTagSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Column {
            Text(
                text = "Find",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Precision guidance to selected tag",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Tag Selector ────────────────────────────────────────────────────
        TagSelectorCard(
            tags = uiState.tags,
            selectedTagId = uiState.selectedTagId,
            onClick = { if (uiState.tags.isNotEmpty()) showTagSheet = true }
        )

        // ── Precision Panel ──────────────────────────────────────────────────
        PrecisionFindingPanel(
            selectedTag = uiState.selectedTag,
            phonePosition = uiState.phonePosition,
            phoneAzimuthDegrees = uiState.phoneAzimuthDegrees,
            onRotatePhoneLeft = onRotatePhoneLeft,
            onRotatePhoneRight = onRotatePhoneRight,
            onResetPhoneAzimuth = onResetPhoneAzimuth,
            onMovePhoneNorth = onMovePhoneNorth,
            onMovePhoneSouth = onMovePhoneSouth,
            onMovePhoneEast = onMovePhoneEast,
            onMovePhoneWest = onMovePhoneWest
        )

        Spacer(modifier = Modifier.height(128.dp))
    }

    if (showTagSheet && uiState.tags.isNotEmpty()) {
        TagSelectorBottomSheet(
            tags = uiState.tags,
            selectedTagId = uiState.selectedTagId,
            geofences = uiState.geofences,
            onSelectTag = {
                onSelectTag(it)
                showTagSheet = false
            },
            onDismiss = { showTagSheet = false }
        )
    }
}

@Composable
private fun TagSelectorCard(
    tags: List<UwbDevice>,
    selectedTagId: String?,
    onClick: () -> Unit
) {
    val selectedTag = tags.firstOrNull { it.id == selectedTagId }
    val label = if (tags.isEmpty()) "No tags available" else selectedTag?.name ?: "Select a tag"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = tags.isNotEmpty()) { onClick() },
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, LightBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PrimaryColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = PrimaryColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Selected tag",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (tags.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (tags.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSelectorBottomSheet(
    tags: List<UwbDevice>,
    selectedTagId: String?,
    geofences: List<Geofence>,
    onSelectTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(LightBorder)
            )
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(
                    text = "Select a tag",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (tags.isEmpty()) "No tags available" else "Choose which tag to guide",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (tags.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No tags available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Add or sync tags to use precision finding.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn {
                    items(tags, key = { it.id }) { tag ->
                        val zone = tag.currentZone(geofences)
                        val isSelected = tag.id == selectedTagId
                        TagOptionRow(
                            tag = tag,
                            zone = zone,
                            selected = isSelected,
                            onClick = {
                                onSelectTag(tag.id)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagOptionRow(
    tag: UwbDevice,
    zone: Geofence?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = if (selected) PrimaryColor.copy(alpha = 0.05f) else Color.Transparent,
        contentColor = if (selected) PrimaryColor else MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (selected) PrimaryColor else LightBorder)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = zone?.let { "${it.name} • ${it.type.label()}" } ?: "Zone unknown",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun UwbDevice.currentZone(geofences: List<Geofence>): Geofence? {
    val containingZones = geofences.filter { GeofenceMath.containsPoint(position, it) }
    return containingZones.firstOrNull { it.type == GeofenceType.RESTRICTED_ZONE }
        ?: containingZones.firstOrNull { it.type == GeofenceType.SAFE_ZONE }
        ?: containingZones.firstOrNull { it.type == GeofenceType.ROOM }
        ?: containingZones.firstOrNull()
}

private fun GeofenceType.label(): String = when (this) {
    GeofenceType.ROOM -> "Room"
    GeofenceType.SAFE_ZONE -> "Safe"
    GeofenceType.RESTRICTED_ZONE -> "Restricted"
}

@Composable
private fun PrecisionFindingPanel(
    selectedTag: UwbDevice?,
    phonePosition: GeoPoint,
    phoneAzimuthDegrees: Double,
    onRotatePhoneLeft: () -> Unit,
    onRotatePhoneRight: () -> Unit,
    onResetPhoneAzimuth: () -> Unit,
    onMovePhoneNorth: () -> Unit,
    onMovePhoneSouth: () -> Unit,
    onMovePhoneEast: () -> Unit,
    onMovePhoneWest: () -> Unit
) {
    if (selectedTag == null) {
        EmptyState()
        return
    }

    val distance = GeoMath.haversineDistanceMeters(phonePosition, selectedTag.position)
    val bearing = GeoMath.initialBearingDegrees(phonePosition, selectedTag.position)
    val arrowRotation = GeoMath.arrowRotationDegrees(bearing, phoneAzimuthDegrees)
    val isNear = distance <= PROXIMITY_THRESHOLD
    val status = distanceStatus(distance)
    val color = statusColor(status)

    // Subtle pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "findPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // ── Main Hero Card ──────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, LightBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Adaptive Visualization
                if (isNear) {
                    ProximityIndicator(pulseScale = pulseScale, pulseAlpha = pulseAlpha)
                } else {
                    CompassRadar(rotation = arrowRotation.toFloat(), arrowScale = pulseScale)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f m", distance),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Status Pill
                    Surface(
                        color = color.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = status.uppercase(),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // ── Controls ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MovementControls(
                onNorth = onMovePhoneNorth,
                onSouth = onMovePhoneSouth,
                onEast = onMovePhoneEast,
                onWest = onMovePhoneWest,
                modifier = Modifier.weight(1.3f)
            )
            RotationControls(
                onLeft = onRotatePhoneLeft,
                onRight = onRotatePhoneRight,
                onReset = onResetPhoneAzimuth,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ProximityIndicator(pulseScale: Float, pulseAlpha: Float) {
    Box(
        modifier = Modifier
            .size(180.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        // Animated Pulse Ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasCenter = center
            drawCircle(
                color = Color(0xFF10B981),
                radius = (size.minDimension / 2f) * pulseScale,
                center = canvasCenter,
                alpha = pulseAlpha
            )
        }

        // Static Target Rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasCenter = center
            val maxRadius = size.minDimension / 2f
            
            // Outer soft surface
            drawCircle(
                color = Color(0xFF10B981).copy(alpha = 0.1f),
                radius = maxRadius * 0.8f,
                center = canvasCenter
            )
            
            // Middle stroke
            drawCircle(
                color = Color(0xFF10B981).copy(alpha = 0.3f),
                radius = maxRadius * 0.55f,
                center = canvasCenter,
                style = Stroke(width = 3f)
            )

            // Inner stroke
            drawCircle(
                color = Color(0xFF10B981).copy(alpha = 0.5f),
                radius = maxRadius * 0.3f,
                center = canvasCenter,
                style = Stroke(width = 4f)
            )
            
            // Solid center dot
            drawCircle(
                color = Color(0xFF10B981),
                radius = 14f,
                center = canvasCenter
            )
        }
    }
}

@Composable
private fun CompassRadar(rotation: Float, arrowScale: Float) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            // Radar Rings
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasCenter = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f * 0.9f
                
                drawCircle(
                    color = LightBorder,
                    radius = maxRadius,
                    center = canvasCenter,
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = LightBorder.copy(alpha = 0.5f),
                    radius = maxRadius * 0.66f,
                    center = canvasCenter,
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = LightBorder.copy(alpha = 0.3f),
                    radius = maxRadius * 0.33f,
                    center = canvasCenter,
                    style = Stroke(width = 2f)
                )
            }

            // Arrow with subtle scale animation
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .rotate(rotation)
                    .scale(0.95f + (arrowScale * 0.05f)), // Subtle 5% pulse
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = PrimaryColor
                )
            }
            
            // Target Dot (always at top in this simple UI)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    color = PrimaryColor,
                    shape = CircleShape,
                    border = BorderStroke(2.dp, Color.White)
                ) {}
            }
        }
    }
}

@Composable
private fun MovementControls(
    onNorth: () -> Unit,
    onSouth: () -> Unit,
    onEast: () -> Unit,
    onWest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, LightBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "Move Phone", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ControlIcon(icon = Icons.Default.KeyboardArrowUp, onClick = onNorth)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ControlIcon(icon = Icons.Default.KeyboardArrowLeft, onClick = onWest)
                    ControlIcon(icon = Icons.Default.KeyboardArrowDown, onClick = onSouth)
                    ControlIcon(icon = Icons.Default.KeyboardArrowRight, onClick = onEast)
                }
            }
        }
    }
}

@Composable
private fun RotationControls(
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, LightBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Rotation", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ControlIcon(icon = Icons.Default.Refresh, onClick = onReset, tint = PrimaryColor)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlIcon(icon = Icons.Default.KeyboardArrowLeft, onClick = onLeft)
                ControlIcon(icon = Icons.Default.KeyboardArrowRight, onClick = onRight)
            }
        }
    }
}

@Composable
private fun ControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = tint
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, LightBorder)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Explore,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Text(
                text = "Select a tag to start precision finding",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}


