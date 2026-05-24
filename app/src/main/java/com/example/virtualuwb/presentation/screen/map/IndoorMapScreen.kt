package com.example.virtualuwb.presentation.screen.map

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.UwbDevice
import com.example.virtualuwb.domain.model.UwbRole
import com.example.virtualuwb.presentation.viewmodel.MapUiState
import com.example.virtualuwb.utils.GeoMath
import com.example.virtualuwb.utils.GeoBounds
import com.example.virtualuwb.utils.MapProjection
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════
// Colors
// ═══════════════════════════════════════════════════════════════════════════

private val AnchorColor = Color(0xFF2962FF)       // Vivid blue
private val TagColor = Color(0xFFD50000)           // Vivid red
private val SelectedHighlight = Color(0xFF00C853)  // Green ring
private val PhoneColor = Color(0xFF7C4DFF)         // Purple
private val CanvasBackground = Color(0xFFF5F5F5)   // Light grey
private val GridColor = Color(0xFFE0E0E0)          // Subtle grid
private val BorderColor = Color(0xFFBDBDBD)         // Border

private val LabelPaint = Paint().apply {
    isAntiAlias = true
    textSize = 32f
    typeface = Typeface.DEFAULT_BOLD
}

// ═══════════════════════════════════════════════════════════════════════════
// Helper format functions (UI-only, no model dependency)
// ═══════════════════════════════════════════════════════════════════════════

private fun formatCoordinate(value: Double): String =
    String.format(Locale.US, "%.6f", value)

private fun formatMeters(value: Double): String =
    String.format(Locale.US, "%.2f m", value)

private fun distanceStatus(distanceMeters: Double): String =
    when {
        distanceMeters < 1.0 -> "Very Near"
        distanceMeters < 3.0 -> "Near"
        distanceMeters < 6.0 -> "Medium"
        else -> "Far"
    }

private fun formatDegrees(value: Double): String =
    String.format(Locale.US, "%.0f°", value)

// ═══════════════════════════════════════════════════════════════════════════
// Main Screen
// ═══════════════════════════════════════════════════════════════════════════

/**
 * MVP indoor-map screen showing anchors, tags, and simulation controls.
 *
 * All geographic → pixel projection happens **inside the Canvas**
 * via [MapProjection.latLonToCanvasOffset]. No pixel data is stored in models.
 */
@Composable
fun IndoorMapScreen(
    uiState: MapUiState,
    onToggleSimulation: () -> Unit,
    onStepSimulation: () -> Unit,
    onResetSimulation: () -> Unit,
    onSelectTag: (String) -> Unit,
    onRotatePhoneLeft: () -> Unit,
    onRotatePhoneRight: () -> Unit,
    onResetPhoneAzimuth: () -> Unit,
    onMovePhoneNorth: () -> Unit,
    onMovePhoneSouth: () -> Unit,
    onMovePhoneEast: () -> Unit,
    onMovePhoneWest: () -> Unit,
    onClearTrails: () -> Unit,
    onAddDevice: (UwbDevice) -> Unit,
    onUpdateDevice: (UwbDevice) -> Unit,
    onDeleteDevice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ── Header ───────────────────────────────────────────────
            Text(
                text = "Virtual UWB Indoor Map",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Lat/Lon based IPS simulation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Info cards ───────────────────────────────────────────
            InfoBar(uiState = uiState)

            Spacer(modifier = Modifier.height(8.dp))

            // ── Tag selector chips ───────────────────────────────────
            TagSelectorRow(
                tags = uiState.tags,
                selectedTagId = uiState.selectedTagId,
                onSelectTag = onSelectTag
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Control buttons ──────────────────────────────────────
            ControlRow(
                isRunning = uiState.isSimulationRunning,
                onToggle = onToggleSimulation,
                onStep = onStepSimulation,
                onReset = onResetSimulation
            )

            // ── Clear Trail button ───────────────────────────────────
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onClearTrails,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Clear Trail", fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Canvas map (fills remaining space) ───────────────────
            IndoorMapCanvas(
                uiState = uiState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Precision Finding Panel ──────────────────────────────
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

            Spacer(modifier = Modifier.height(8.dp))

            // ── Selected Tag Detail Panel ────────────────────────────
            SelectedTagDetailPanel(
                selectedTag = uiState.selectedTag,
                anchors = uiState.anchors
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Device Management Panel ──────────────────────────────
            DeviceManagementPanel(
                devices = uiState.devices,
                onAddDevice = onAddDevice,
                onUpdateDevice = onUpdateDevice,
                onDeleteDevice = onDeleteDevice
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Tag Selector Row
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun TagSelectorRow(
    tags: List<UwbDevice>,
    selectedTagId: String?,
    onSelectTag: (String) -> Unit
) {
    if (tags.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (tag in tags) {
            val isSelected = tag.id == selectedTagId
            if (isSelected) {
                Button(
                    onClick = { onSelectTag(tag.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TagColor
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(text = tag.name, fontSize = 13.sp)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelectTag(tag.id) },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(text = tag.name, fontSize = 13.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Selected Tag Detail Panel
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SelectedTagDetailPanel(
    selectedTag: UwbDevice?,
    anchors: List<UwbDevice>,
    modifier: Modifier = Modifier
) {
    if (selectedTag == null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "No tag selected",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Compute distances (UI-only, not stored in model)
    val distances = anchors.map { anchor ->
        anchor to GeoMath.haversineDistanceMeters(anchor.position, selectedTag.position)
    }
    val nearest = distances.minByOrNull { it.second }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // ── Tracking header ──────────────────────────────────────
            Text(
                text = "Tracking: ${selectedTag.name}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TagColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            // ── Coordinates ──────────────────────────────────────────
            DetailRow(label = "Lat", value = formatCoordinate(selectedTag.latitude))
            DetailRow(label = "Lon", value = formatCoordinate(selectedTag.longitude))
            DetailRow(label = "Updated", value = "Live")

            Spacer(modifier = Modifier.height(6.dp))

            // ── Nearest anchor ───────────────────────────────────────
            if (nearest != null) {
                val (nearestAnchor, nearestDistance) = nearest
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                DetailRow(label = "Nearest anchor", value = nearestAnchor.name)
                DetailRow(label = "Distance", value = formatMeters(nearestDistance))
                DetailRow(label = "Status", value = distanceStatus(nearestDistance))
            }

            // ── All anchor distances ─────────────────────────────────
            if (distances.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "All Distances",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AnchorColor
                )
                Spacer(modifier = Modifier.height(4.dp))

                for ((anchor, distance) in distances) {
                    DetailRow(
                        label = anchor.name,
                        value = formatMeters(distance)
                    )
                }
            }
        }
    }
}

/**
 * Simple key-value row used inside the detail panel.
 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Info Bar
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun InfoBar(uiState: MapUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoCard(
            label = "Anchors",
            value = "${uiState.anchorCount}",
            color = AnchorColor,
            modifier = Modifier.weight(1f)
        )
        InfoCard(
            label = "Tags",
            value = "${uiState.tagCount}",
            color = TagColor,
            modifier = Modifier.weight(1f)
        )
        InfoCard(
            label = "Simulation",
            value = if (uiState.isSimulationRunning) "Running" else "Stopped",
            color = if (uiState.isSimulationRunning) SelectedHighlight else Color.Gray,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text(
        text = "Selected: ${uiState.selectedTag?.name ?: "None"}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun InfoCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Control Buttons
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ControlRow(
    isRunning: Boolean,
    onToggle: () -> Unit,
    onStep: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onToggle,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Text(text = if (isRunning) "Pause" else "Start")
        }

        OutlinedButton(
            onClick = onStep,
            modifier = Modifier.weight(1f),
            enabled = !isRunning
        ) {
            Text(text = "Step")
        }

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.weight(1f)
        ) {
            Text(text = "Reset")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Canvas Map
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun IndoorMapCanvas(
    uiState: MapUiState,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.background(CanvasBackground)
    ) {
        val w = size.width
        val h = size.height
        val padding = 40f

        // ── Grid ─────────────────────────────────────────────────────
        drawGrid(
            canvasWidth = w,
            canvasHeight = h,
            horizontalLines = 5,
            verticalLines = 5
        )

        // ── Border ───────────────────────────────────────────────────
        drawRect(
            color = BorderColor,
            style = Stroke(width = 2f)
        )

        // ── Devices ──────────────────────────────────────────────────
        if (!uiState.hasDevices) return@Canvas

        val bounds = uiState.bounds

        // ── Tag trails (drawn first so nodes render on top) ──────
        uiState.tagTrails.forEach { (tagId, trail) ->
            drawTagTrail(
                trail = trail,
                bounds = bounds,
                canvasWidth = w,
                canvasHeight = h,
                paddingPx = padding,
                isSelected = tagId == uiState.selectedTagId
            )
        }

        for (device in uiState.devices) {
            val offset = MapProjection.latLonToCanvasOffset(
                point = device.position,
                bounds = bounds,
                canvasWidth = w,
                canvasHeight = h,
                paddingPx = padding
            )

            if (device.isAnchor) {
                drawAnchor(offset, device)
            } else {
                val isSelected = device.id == uiState.selectedTagId
                drawTag(offset, device, isSelected)
            }
        }

        // ── Phone marker ─────────────────────────────────────────────
        val phoneOffset = MapProjection.latLonToCanvasOffset(
            point = uiState.phonePosition,
            bounds = bounds,
            canvasWidth = w,
            canvasHeight = h,
            paddingPx = padding
        )
        drawPhone(phoneOffset, uiState.phoneAzimuthDegrees)
    }
}

/**
 * Draws a tag's movement trail as connected line segments.
 * Selected tag trails are brighter and thicker.
 */
private fun DrawScope.drawTagTrail(
    trail: List<GeoPoint>,
    bounds: GeoBounds,
    canvasWidth: Float,
    canvasHeight: Float,
    paddingPx: Float,
    isSelected: Boolean
) {
    if (trail.size < 2) return

    val color = if (isSelected) SelectedHighlight.copy(alpha = 0.65f)
                else TagColor.copy(alpha = 0.25f)
    val strokeW = if (isSelected) 4f else 2f

    for (i in 0 until trail.lastIndex) {
        val from = MapProjection.latLonToCanvasOffset(
            point = trail[i], bounds = bounds,
            canvasWidth = canvasWidth, canvasHeight = canvasHeight,
            paddingPx = paddingPx
        )
        val to = MapProjection.latLonToCanvasOffset(
            point = trail[i + 1], bounds = bounds,
            canvasWidth = canvasWidth, canvasHeight = canvasHeight,
            paddingPx = paddingPx
        )
        drawLine(color = color, start = from, end = to, strokeWidth = strokeW)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// DrawScope helpers
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Draws a simple grid of horizontal and vertical lines.
 */
private fun DrawScope.drawGrid(
    canvasWidth: Float,
    canvasHeight: Float,
    horizontalLines: Int,
    verticalLines: Int
) {
    // Horizontal lines
    for (i in 1..horizontalLines) {
        val y = canvasHeight * i / (horizontalLines + 1)
        drawLine(
            color = GridColor,
            start = Offset(0f, y),
            end = Offset(canvasWidth, y),
            strokeWidth = 1f
        )
    }
    // Vertical lines
    for (i in 1..verticalLines) {
        val x = canvasWidth * i / (verticalLines + 1)
        drawLine(
            color = GridColor,
            start = Offset(x, 0f),
            end = Offset(x, canvasHeight),
            strokeWidth = 1f
        )
    }
}

/**
 * Draws an Anchor node as a filled square with a label.
 */
private fun DrawScope.drawAnchor(center: Offset, device: UwbDevice) {
    val halfSize = 14f

    // Filled square
    drawRect(
        color = AnchorColor,
        topLeft = Offset(center.x - halfSize, center.y - halfSize),
        size = androidx.compose.ui.geometry.Size(halfSize * 2, halfSize * 2)
    )

    // Outer ring for emphasis
    drawRect(
        color = AnchorColor.copy(alpha = 0.3f),
        topLeft = Offset(center.x - halfSize - 4f, center.y - halfSize - 4f),
        size = androidx.compose.ui.geometry.Size(halfSize * 2 + 8f, halfSize * 2 + 8f),
        style = Stroke(width = 2f)
    )

    // Label
    drawDeviceLabel(
        text = device.name,
        center = center,
        color = AnchorColor,
        offsetY = halfSize + 20f
    )
}

/**
 * Draws a Tag node as a filled circle with a label.
 * If [isSelected], an additional green highlight ring is drawn.
 */
private fun DrawScope.drawTag(
    center: Offset,
    device: UwbDevice,
    isSelected: Boolean
) {
    val radius = 12f

    // Selected highlight ring
    if (isSelected) {
        drawCircle(
            color = SelectedHighlight,
            radius = radius + 10f,
            center = center,
            style = Stroke(width = 3f)
        )
        drawCircle(
            color = SelectedHighlight.copy(alpha = 0.15f),
            radius = radius + 10f,
            center = center
        )
    }

    // Filled circle
    drawCircle(color = TagColor, radius = radius, center = center)

    // White inner dot
    drawCircle(color = Color.White, radius = 4f, center = center)

    // Label
    drawDeviceLabel(
        text = device.name,
        center = center,
        color = TagColor,
        offsetY = radius + 20f
    )
}

/**
 * Draws a text label below a device node using the native Canvas.
 */
private fun DrawScope.drawDeviceLabel(
    text: String,
    center: Offset,
    color: Color,
    offsetY: Float
) {
    drawContext.canvas.nativeCanvas.drawText(
        text,
        center.x - LabelPaint.measureText(text) / 2f,
        center.y + offsetY,
        LabelPaint.apply {
            this.color = android.graphics.Color.argb(
                (color.alpha * 255).toInt(),
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt()
            )
        }
    )
}

/**
 * Draws the simulated Phone marker with a heading line.
 */
private fun DrawScope.drawPhone(center: Offset, azimuthDegrees: Double) {
    val radius = 14f

    // Outer glow
    drawCircle(
        color = PhoneColor.copy(alpha = 0.2f),
        radius = radius + 8f,
        center = center
    )

    // Filled circle
    drawCircle(color = PhoneColor, radius = radius, center = center)

    // White inner dot
    drawCircle(color = Color.White, radius = 5f, center = center)

    // Heading line: azimuth 0° = North = up on canvas (dy negative)
    val lineLength = 30f
    val azimuthRad = Math.toRadians(azimuthDegrees)
    val dx = (sin(azimuthRad) * lineLength).toFloat()
    val dy = (-cos(azimuthRad) * lineLength).toFloat()
    drawLine(
        color = PhoneColor,
        start = center,
        end = Offset(center.x + dx, center.y + dy),
        strokeWidth = 3f
    )

    // Label
    drawDeviceLabel(
        text = "Phone",
        center = center,
        color = PhoneColor,
        offsetY = radius + 20f
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// Precision Finding Panel
// ═══════════════════════════════════════════════════════════════════════════

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
    onMovePhoneWest: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedTag == null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Select a tag to start Precision Finding",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Compute finding values (UI-only)
    val distance = GeoMath.haversineDistanceMeters(phonePosition, selectedTag.position)
    val bearing = GeoMath.initialBearingDegrees(phonePosition, selectedTag.position)
    val rotation = GeoMath.arrowRotationDegrees(bearing, phoneAzimuthDegrees)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Title ────────────────────────────────────────────────
            Text(
                text = "Precision Finding",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PhoneColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Arrow indicator ──────────────────────────────────────
            Text(
                text = "↑",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = SelectedHighlight,
                textAlign = TextAlign.Center,
                modifier = Modifier.rotate(rotation.toFloat())
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ── Info rows ────────────────────────────────────────────
            DetailRow(label = "Distance", value = formatMeters(distance))
            DetailRow(label = "Status", value = distanceStatus(distance))
            DetailRow(label = "Bearing", value = formatDegrees(bearing))
            DetailRow(label = "Phone azimuth", value = formatDegrees(phoneAzimuthDegrees))
            DetailRow(label = "Arrow rotation", value = formatDegrees(rotation))

            Spacer(modifier = Modifier.height(8.dp))

            // ── Rotation controls ────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onRotatePhoneLeft,
                    modifier = Modifier.weight(1f)
                ) { Text("← Rotate", fontSize = 11.sp) }

                OutlinedButton(
                    onClick = onResetPhoneAzimuth,
                    modifier = Modifier.weight(1f)
                ) { Text("Reset", fontSize = 11.sp) }

                OutlinedButton(
                    onClick = onRotatePhoneRight,
                    modifier = Modifier.weight(1f)
                ) { Text("Rotate →", fontSize = 11.sp) }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── D-pad movement controls ──────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedButton(onClick = onMovePhoneNorth) {
                    Text("N ↑", fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onMovePhoneWest) {
                        Text("W ←", fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = onMovePhoneEast) {
                        Text("→ E", fontSize = 11.sp)
                    }
                }
                OutlinedButton(onClick = onMovePhoneSouth) {
                    Text("S ↓", fontSize = 11.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Device Management Panel
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun DeviceManagementPanel(
    devices: List<UwbDevice>,
    onAddDevice: (UwbDevice) -> Unit,
    onUpdateDevice: (UwbDevice) -> Unit,
    onDeleteDevice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFormVisible by rememberSaveable { mutableStateOf(false) }
    var editingDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var roleInput by rememberSaveable { mutableStateOf(UwbRole.TAG.name) }
    var latitudeInput by rememberSaveable { mutableStateOf("") }
    var longitudeInput by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Header ───────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Device Management",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!isFormVisible) {
                    Button(onClick = {
                        editingDeviceId = null
                        nameInput = ""
                        roleInput = UwbRole.TAG.name
                        latitudeInput = ""
                        longitudeInput = ""
                        errorMessage = null
                        isFormVisible = true
                    }) {
                        Text("Add Device", fontSize = 12.sp)
                    }
                }
            }

            if (isFormVisible) {
                HorizontalDivider()
                // ── Form ─────────────────────────────────────────────
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Device Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isAnchor = roleInput == UwbRole.ANCHOR.name
                    OutlinedButton(
                        onClick = { roleInput = UwbRole.ANCHOR.name },
                        modifier = Modifier.weight(1f),
                        colors = if (isAnchor) ButtonDefaults.outlinedButtonColors(containerColor = AnchorColor.copy(alpha = 0.1f)) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("ANCHOR", color = if (isAnchor) AnchorColor else MaterialTheme.colorScheme.onSurface)
                    }
                    val isTag = roleInput == UwbRole.TAG.name
                    OutlinedButton(
                        onClick = { roleInput = UwbRole.TAG.name },
                        modifier = Modifier.weight(1f),
                        colors = if (isTag) ButtonDefaults.outlinedButtonColors(containerColor = TagColor.copy(alpha = 0.1f)) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("TAG", color = if (isTag) TagColor else MaterialTheme.colorScheme.onSurface)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = latitudeInput,
                        onValueChange = { latitudeInput = it },
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = longitudeInput,
                        onValueChange = { longitudeInput = it },
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { isFormVisible = false }) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        // Validation
                        val name = nameInput.trim()
                        if (name.isEmpty()) {
                            errorMessage = "Name cannot be empty"
                            return@Button
                        }
                        val lat = latitudeInput.toDoubleOrNull()
                        val lon = longitudeInput.toDoubleOrNull()
                        if (lat == null || lon == null || !GeoPoint.isValid(lat, lon)) {
                            errorMessage = "Invalid coordinates"
                            return@Button
                        }
                        val role = runCatching { UwbRole.valueOf(roleInput) }.getOrNull()
                        if (role == null) {
                            errorMessage = "Invalid role"
                            return@Button
                        }

                        val geoPoint = GeoPoint(lat, lon)

                        if (editingDeviceId == null) {
                            val prefix = if (role == UwbRole.ANCHOR) "anchor" else "tag"
                            val id = "$prefix-${System.currentTimeMillis()}"
                            onAddDevice(UwbDevice(id, name, role, geoPoint))
                        } else {
                            onUpdateDevice(UwbDevice(editingDeviceId!!, name, role, geoPoint))
                        }
                        
                        isFormVisible = false
                    }) {
                        Text(if (editingDeviceId == null) "Create Device" else "Update Device")
                    }
                }
            } else {
                // ── Device List ──────────────────────────────────────
                if (devices.isEmpty()) {
                    Text(
                        text = "No devices found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    devices.forEach { device ->
                        DeviceRow(
                            device = device,
                            onEdit = {
                                editingDeviceId = device.id
                                nameInput = device.name
                                roleInput = device.role.name
                                latitudeInput = device.latitude.toString()
                                longitudeInput = device.longitude.toString()
                                errorMessage = null
                                isFormVisible = true
                            },
                            onDelete = {
                                onDeleteDevice(device.id)
                                if (editingDeviceId == device.id) {
                                    isFormVisible = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: UwbDevice,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = "${device.role.name} • Lat: ${formatCoordinate(device.latitude)}, Lon: ${formatCoordinate(device.longitude)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                TextButton(onClick = onEdit) {
                    Text("Edit", fontSize = 12.sp)
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
