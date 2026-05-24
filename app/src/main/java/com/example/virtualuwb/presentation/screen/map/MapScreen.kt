package com.example.virtualuwb.presentation.screen.map

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.GeofenceType
import com.example.virtualuwb.domain.model.UwbDevice
import com.example.virtualuwb.presentation.viewmodel.MapUiState
import com.example.virtualuwb.utils.GeoBounds
import com.example.virtualuwb.utils.GeofenceMath
import com.example.virtualuwb.utils.MapProjection
import com.example.virtualuwb.domain.model.Geofence
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private val AnchorColor = Color(0xFF2962FF)
private val TagColor = Color(0xFFD50000)
private val SelectedHighlight = Color(0xFF00C853)
private val PhoneColor = Color(0xFF7C4DFF)
private val CanvasBackground = Color(0xFFFFFFFF)
private val GridColor = Color(0xFFF3F4F6)
private val LightBorder = Color(0xFFE5E7EB)
private val ScreenBackground = Color(0xFFF9FAFB)
private val SafeContainer = Color(0xFFE7F7EE)
private val SafeContent = Color(0xFF1B5E20)
private val WarningContainer = Color(0xFFFDECEA)
private val WarningContent = Color(0xFFD32F2F)

private val LabelPaint = Paint().apply {
    isAntiAlias = true
    textSize = 30f
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}

private val LabelBackgroundPaint = Paint().apply {
    isAntiAlias = true
}

private val LabelBorderPaint = Paint().apply {
    isAntiAlias = true
    style = Paint.Style.STROKE
    strokeWidth = 1.5f
}

private enum class PhoneZoneStatus {
    SAFE,
    RESTRICTED,
    UNKNOWN
}

@Composable
fun MapScreen(
    uiState: MapUiState,
    onToggleSimulation: () -> Unit,
    onStepSimulation: () -> Unit,
    onResetSimulation: () -> Unit,
    onSelectTag: (String) -> Unit,
    onClearTrails: () -> Unit,
    onTriggerDemoGeofenceEvent: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isTagInfoVisible by remember { mutableStateOf(false) }
    val pulseProgress by rememberInfiniteTransition(label = "map-pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse-progress"
    )
    val restrictedTags = remember(uiState.tags, uiState.restrictedGeofences) {
        uiState.tags.filter { tag ->
            GeofenceMath.findContainingGeofences(tag.position, uiState.restrictedGeofences).isNotEmpty()
        }
    }
    val safeGeofences = remember(uiState.geofences) {
        uiState.geofences.filter { it.type == GeofenceType.SAFE_ZONE }
    }
    val safeTagCount = remember(uiState.tags, safeGeofences) {
        uiState.tags.count { tag ->
            safeGeofences.any { geofence -> GeofenceMath.containsPoint(tag.position, geofence) }
        }
    }
    val phoneZoneStatus = remember(uiState.phonePosition, uiState.geofences) {
        resolvePhoneZoneStatus(phonePosition = uiState.phonePosition, geofences = uiState.geofences)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = "Virtual UWB",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Realtime indoor positioning",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        InfoBar(uiState = uiState)

        StatusAlertCard(
            trackedTagCount = uiState.tagCount,
            safeTagCount = safeTagCount,
            restrictedTags = restrictedTags,
            phoneZoneStatus = phoneZoneStatus
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, LightBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(590.dp)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        IndoorMapCanvas(
                            uiState = uiState,
                            pulseProgress = pulseProgress,
                            onTagTapped = { tagId ->
                                onSelectTag(tagId)
                                isTagInfoVisible = true
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    CompactLegendRow()
                }

                // Floating Tag Info Overlay
                if (isTagInfoVisible && uiState.selectedTag != null) {
                    TagInfoOverlay(
                        tag = uiState.selectedTag!!,
                        anchors = uiState.anchors,
                        geofences = uiState.geofences,
                        onDismiss = { isTagInfoVisible = false },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 80.dp) // Offset above control row
                            .padding(horizontal = 12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun StatusAlertCard(
    trackedTagCount: Int,
    safeTagCount: Int,
    restrictedTags: List<UwbDevice>,
    phoneZoneStatus: PhoneZoneStatus
) {
    val isDanger = restrictedTags.isNotEmpty()
    val containerColor = if (isDanger) WarningContainer else SafeContainer
    val contentColor = if (isDanger) WarningContent else SafeContent
    val title = if (isDanger) "Restricted zone alert" else "All tags safe"
    val icon = if (isDanger) Icons.Default.Warning else Icons.Default.Info
    val summaryLine = "$trackedTagCount tags tracked · $safeTagCount safe · ${restrictedTags.size} restricted"
    val affectedLabel = when {
        restrictedTags.size <= 2 -> "Affected: ${restrictedTags.joinToString { it.name }}"
        else -> {
            val firstNames = restrictedTags.take(2).joinToString { it.name }
            "${restrictedTags.size} tags affected: $firstNames +${restrictedTags.size - 2} more"
        }
    }
    val phoneSummary = when (phoneZoneStatus) {
        PhoneZoneStatus.SAFE -> "Phone: Safe Zone"
        PhoneZoneStatus.RESTRICTED -> "Phone: Restricted Zone"
        PhoneZoneStatus.UNKNOWN -> "Phone: Unknown"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = summaryLine,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                if (isDanger) {
                    Text(
                        text = affectedLabel,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = phoneSummary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CompactLegendRow() {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CompactLegendItem(color = AnchorColor, label = "Anchor", dot = true)
        CompactLegendItem(color = TagColor, label = "Tag", dot = true)
        CompactLegendItem(color = PhoneColor, label = "Phone", dot = true)
        CompactLegendItem(color = Color(0xFF2E7D32), label = "Safe", dot = false)
        CompactLegendItem(color = Color(0xFFC62828), label = "Restricted", dot = false)
    }
}

@Composable
private fun CompactLegendItem(color: Color, label: String, dot: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(if (dot) CircleShape else RoundedCornerShape(1.dp))
                .background(color)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TagInfoOverlay(
    tag: UwbDevice,
    anchors: List<UwbDevice>,
    geofences: List<Geofence>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nearestInfo = getNearestAnchorInfo(tag, anchors)
    val containingZones = GeofenceMath.findContainingGeofences(tag.position, geofences)
    val zoneLabel = if (containingZones.isEmpty()) "None" else containingZones.joinToString { it.name }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(1.dp, LightBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(TagColor))
                    Text(text = tag.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    
                    Surface(
                        color = TagColor.copy(alpha = 0.1f),
                        contentColor = TagColor,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "ACTIVE",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                
                Surface(
                    onClick = onDismiss,
                    color = Color.Transparent,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(18.dp),
                        tint = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OverlayDetailRow(label = "Dist", value = nearestInfo?.second?.let { formatDistance(it) } ?: "N/A")
                OverlayDetailRow(label = "Zone", value = zoneLabel)
                OverlayDetailRow(label = "Near", value = nearestInfo?.first ?: "N/A")
            }
        }
    }
}

@Composable
private fun OverlayDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}



@Composable
private fun InfoBar(uiState: MapUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoChip(
                label = "Anchors",
                value = "${uiState.anchorCount}",
                color = AnchorColor,
                modifier = Modifier.weight(1f)
            )
            InfoChip(
                label = "Tags",
                value = "${uiState.tagCount}",
                color = TagColor,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoChip(
                label = "Source",
                value = uiState.dataSourceMode.name,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            InfoChip(
                label = "Status",
                value = if (uiState.isSimulationRunning) "Live" else "Paused",
                color = if (uiState.isSimulationRunning) SelectedHighlight else Color.Gray,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun InfoChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.08f),
        contentColor = color,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            Text(text = value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IndoorMapCanvas(
    uiState: MapUiState,
    pulseProgress: Float,
    onTagTapped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val bounds = uiState.bounds
    val padding = 40f
    
    Canvas(
        modifier = modifier
            .background(CanvasBackground)
            .border(1.dp, LightBorder.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .pointerInput(uiState.devices, bounds) {
                detectTapGestures { tapOffset ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    
                    // Hit testing for tags
                    val hitRadius = with(density) { 24.dp.toPx() }
                    val clickedTag = uiState.tags.find { tag ->
                        val tagOffset = MapProjection.latLonToCanvasOffset(
                            point = tag.position,
                            bounds = bounds,
                            canvasWidth = w,
                            canvasHeight = h,
                            paddingPx = padding
                        )
                        val dist = (tagOffset - tapOffset).getDistance()
                        dist < hitRadius
                    }
                    
                    if (clickedTag != null) {
                        onTagTapped(clickedTag.id)
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height

        drawGrid(canvasWidth = w, canvasHeight = h, horizontalLines = 8, verticalLines = 8)

        if (uiState.hasDevices || uiState.geofences.isNotEmpty()) {
            for (geofence in uiState.geofences) {
                drawGeofence(
                    geofence = geofence,
                    bounds = bounds,
                    canvasWidth = w,
                    canvasHeight = h,
                    paddingPx = padding
                )
            }

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
                    drawTag(
                        center = offset,
                        device = device,
                        isSelected = device.id == uiState.selectedTagId,
                        pulseProgress = pulseProgress
                    )
                }
            }

            val phoneOffset = MapProjection.latLonToCanvasOffset(
                point = uiState.phonePosition,
                bounds = bounds,
                canvasWidth = w,
                canvasHeight = h,
                paddingPx = padding
            )
            drawPhone(phoneOffset, uiState.phoneAzimuthDegrees, pulseProgress)
        }
    }
}

private fun DrawScope.drawTagTrail(
    trail: List<GeoPoint>,
    bounds: GeoBounds,
    canvasWidth: Float,
    canvasHeight: Float,
    paddingPx: Float,
    isSelected: Boolean
) {
    if (trail.size < 2) return

    val color = if (isSelected) SelectedHighlight.copy(alpha = 0.65f) else TagColor.copy(alpha = 0.25f)
    val strokeW = if (isSelected) 4f else 2f

    for (i in 0 until trail.lastIndex) {
        val from = MapProjection.latLonToCanvasOffset(
            point = trail[i], bounds = bounds,
            canvasWidth = canvasWidth, canvasHeight = canvasHeight, paddingPx = paddingPx
        )
        val to = MapProjection.latLonToCanvasOffset(
            point = trail[i + 1], bounds = bounds,
            canvasWidth = canvasWidth, canvasHeight = canvasHeight, paddingPx = paddingPx
        )
        drawLine(color = color, start = from, end = to, strokeWidth = strokeW)
    }
}

private fun DrawScope.drawGrid(
    canvasWidth: Float,
    canvasHeight: Float,
    horizontalLines: Int,
    verticalLines: Int
) {
    for (i in 1..horizontalLines) {
        val y = canvasHeight * i / (horizontalLines + 1)
        drawLine(color = GridColor, start = Offset(0f, y), end = Offset(canvasWidth, y), strokeWidth = 1f)
    }
    for (i in 1..verticalLines) {
        val x = canvasWidth * i / (verticalLines + 1)
        drawLine(color = GridColor, start = Offset(x, 0f), end = Offset(x, canvasHeight), strokeWidth = 1f)
    }
}

private fun DrawScope.drawAnchor(center: Offset, device: UwbDevice) {
    val halfSize = 14f
    drawRect(
        color = AnchorColor,
        topLeft = Offset(center.x - halfSize, center.y - halfSize),
        size = androidx.compose.ui.geometry.Size(halfSize * 2, halfSize * 2)
    )
    drawRect(
        color = AnchorColor.copy(alpha = 0.3f),
        topLeft = Offset(center.x - halfSize - 4f, center.y - halfSize - 4f),
        size = androidx.compose.ui.geometry.Size(halfSize * 2 + 8f, halfSize * 2 + 8f),
        style = Stroke(width = 2f)
    )
    drawDeviceLabel(text = compactDeviceLabel(device), center = center, color = AnchorColor, offsetY = halfSize + 16f)
}

private fun DrawScope.drawTag(center: Offset, device: UwbDevice, isSelected: Boolean, pulseProgress: Float) {
    val radius = 12f
    val pulseRadius = radius + 10f + (pulseProgress * 14f)
    val pulseAlpha = (0.16f * (1f - pulseProgress)).coerceAtLeast(0f)
    drawCircle(
        color = TagColor.copy(alpha = pulseAlpha),
        radius = pulseRadius,
        center = center,
        style = Stroke(width = 2f)
    )

    if (isSelected) {
        drawCircle(color = TagColor.copy(alpha = 0.16f), radius = radius + 12f, center = center)
        drawCircle(color = TagColor.copy(alpha = 0.85f), radius = radius + 10f, center = center, style = Stroke(width = 3f))
    }
    drawCircle(color = TagColor, radius = radius, center = center)
    drawCircle(color = Color.White, radius = 4f, center = center)
    drawDeviceLabel(text = compactDeviceLabel(device), center = center, color = TagColor, offsetY = radius + 20f)
}

private fun DrawScope.drawDeviceLabel(text: String, center: Offset, color: Color, offsetY: Float) {
    val baselineY = center.y + offsetY
    val textWidth = LabelPaint.measureText(text)
    val fontMetrics = LabelPaint.fontMetrics
    val horizontalPadding = 6f
    val verticalPadding = 3f
    val left = center.x - (textWidth / 2f) - horizontalPadding
    val top = baselineY + fontMetrics.ascent - verticalPadding
    val right = center.x + (textWidth / 2f) + horizontalPadding
    val bottom = baselineY + fontMetrics.descent + verticalPadding
    val cornerRadius = 10f
    val rect = RectF(left, top, right, bottom)

    LabelBackgroundPaint.color = android.graphics.Color.argb(205, 255, 255, 255)
    LabelBorderPaint.color = android.graphics.Color.argb(40, 0, 0, 0)

    drawContext.canvas.nativeCanvas.apply {
        // subtle shadow under label for readability on geofence fills
        LabelBackgroundPaint.setShadowLayer(3f, 0f, 1f, android.graphics.Color.argb(28, 0, 0, 0))
        drawRoundRect(rect, cornerRadius, cornerRadius, LabelBackgroundPaint)
        LabelBackgroundPaint.clearShadowLayer()
        drawRoundRect(rect, cornerRadius, cornerRadius, LabelBorderPaint)
        drawText(
            text,
            center.x - textWidth / 2f,
            baselineY,
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
}

private fun DrawScope.drawPhone(center: Offset, azimuthDegrees: Double, pulseProgress: Float) {
    val radius = 14f
    val pulseRadius = radius + 10f + (pulseProgress * 16f)
    val pulseAlpha = (0.14f * (1f - pulseProgress)).coerceAtLeast(0f)
    drawCircle(
        color = PhoneColor.copy(alpha = pulseAlpha),
        radius = pulseRadius,
        center = center,
        style = Stroke(width = 2f)
    )
    drawCircle(color = PhoneColor.copy(alpha = 0.2f), radius = radius + 8f, center = center)
    drawCircle(color = PhoneColor, radius = radius, center = center)
    drawCircle(color = Color.White, radius = 5f, center = center)
    
    val lineLength = 30f
    val azimuthRad = Math.toRadians(azimuthDegrees)
    val dx = (sin(azimuthRad) * lineLength).toFloat()
    val dy = (-cos(azimuthRad) * lineLength).toFloat()
    drawLine(color = PhoneColor, start = center, end = Offset(center.x + dx, center.y + dy), strokeWidth = 3f)
    
    drawDeviceLabel(text = "Phone", center = center, color = PhoneColor, offsetY = radius + 20f)
}

private fun DrawScope.drawGeofence(
    geofence: Geofence,
    bounds: GeoBounds,
    canvasWidth: Float,
    canvasHeight: Float,
    paddingPx: Float
) {
    if (geofence.vertices.size < 3) return

    val projectedVertices = geofence.vertices.map { geoPoint ->
        MapProjection.latLonToCanvasOffset(
            point = geoPoint,
            bounds = bounds,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            paddingPx = paddingPx
        )
    }

    val path = androidx.compose.ui.graphics.Path()
    projectedVertices.forEachIndexed { index, offset ->
        if (index == 0) path.moveTo(offset.x, offset.y)
        else path.lineTo(offset.x, offset.y)
    }
    path.close()

    val (fillColor, strokeColor) = when (geofence.type) {
        GeofenceType.RESTRICTED_ZONE -> Color.Red.copy(alpha = 0.15f) to Color.Red.copy(alpha = 0.7f)
        GeofenceType.SAFE_ZONE -> Color.Green.copy(alpha = 0.12f) to Color.Green.copy(alpha = 0.7f)
        GeofenceType.ROOM -> Color.Gray.copy(alpha = 0.08f) to Color.Gray.copy(alpha = 0.7f)
    }

    drawPath(path = path, color = fillColor)
    drawPath(path = path, color = strokeColor, style = Stroke(width = 3f))

    if (geofence.type != GeofenceType.ROOM) {
        val topLeft = Offset(
            x = projectedVertices.minOf { it.x },
            y = projectedVertices.minOf { it.y }
        )
        val zoneLabelAnchor = Offset(topLeft.x + 18f, topLeft.y + 14f)
        drawDeviceLabel(text = geofence.name, center = zoneLabelAnchor, color = strokeColor, offsetY = 0f)
    }
}

private fun resolvePhoneZoneStatus(phonePosition: GeoPoint, geofences: List<Geofence>): PhoneZoneStatus {
    val containing = geofences.filter { geofence -> GeofenceMath.containsPoint(phonePosition, geofence) }

    return when {
        containing.any { it.type == GeofenceType.RESTRICTED_ZONE } -> PhoneZoneStatus.RESTRICTED
        containing.any { it.type == GeofenceType.SAFE_ZONE } -> PhoneZoneStatus.SAFE
        else -> PhoneZoneStatus.UNKNOWN
    }
}

private fun compactDeviceLabel(device: UwbDevice): String {
    return device.name
}

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

private fun formatDistance(meters: Double): String = if (meters < 1000) {
    String.format(Locale.US, "%.2f m", meters)
} else {
    String.format(Locale.US, "%.2f km", meters / 1000.0)
}

private fun calculateDistance(p1: GeoPoint, p2: GeoPoint): Double {
    val lat1 = Math.toRadians(p1.latitude)
    val lon1 = Math.toRadians(p1.longitude)
    val lat2 = Math.toRadians(p2.latitude)
    val lon2 = Math.toRadians(p2.longitude)

    val dLat = lat2 - lat1
    val dLon = lon2 - lon1

    val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return 6371000.0 * c // in meters
}

private fun getNearestAnchorInfo(tag: UwbDevice, anchors: List<UwbDevice>): Pair<String, Double>? {
    if (anchors.isEmpty()) return null

    var nearest: UwbDevice? = null
    var minDistance = Double.MAX_VALUE

    for (anchor in anchors) {
        val dist = calculateDistance(tag.position, anchor.position)
        if (dist < minDistance) {
            minDistance = dist
            nearest = anchor
        }
    }

    return nearest?.let { it.name to minDistance }
}
