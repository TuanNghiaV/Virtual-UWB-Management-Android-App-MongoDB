package com.example.virtualuwb.presentation.screen.debug

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.virtualuwb.BuildConfig
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.UwbDevice
import com.example.virtualuwb.domain.model.DataSourceMode
import com.example.virtualuwb.presentation.viewmodel.MapUiState
import com.example.virtualuwb.utils.GeofenceMath
import com.example.virtualuwb.utils.GeoMath
import android.util.Log
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import android.widget.Toast

private val PrimaryIndigo = Color(0xFF6366F1)
private val SuccessGreen = Color(0xFF10B981)
private val WarningOrange = Color(0xFFF59E0B)
private val ErrorRed = Color(0xFFEF4444)
private val LightBorder = Color(0xFFE5E7EB)
private val ScreenBackground = Color(0xFFF9FAFB)

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)
private fun formatMeters(value: Double): String = String.format(Locale.US, "%.2f m", value)

private fun tagMovementSpeedLabel(speed: Int): String = when (speed.coerceIn(0, 5)) {
    0 -> "Idle"
    1 -> "Slow"
    2 -> "Normal"
    3 -> "Fast"
    4 -> "Very fast"
    else -> "Demo boost"
}

private fun distanceStatus(distanceMeters: Double): String =
    when {
        distanceMeters < 1.0 -> "Near"
        distanceMeters < 5.0 -> "Medium"
        else -> "Far"
    }

@Composable
fun DebugScreen(
    uiState: MapUiState,
    onToggleDataSourceMode: () -> Unit,
    onTogglePositionLogging: () -> Unit,
    onMoveAllTagsToSafeZone: suspend () -> String,
    onMoveAllTagsToRestrictedZone: suspend () -> String,
    onRandomizeTagsInsideAnchors: suspend () -> String,
    onTagMovementSpeedChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // Local ephemeral states for testing
    var isTestingBackend by remember { mutableStateOf(false) }
    var backendTestResult by remember { mutableStateOf<String?>(null) }
    var isTestingPositionLog by remember { mutableStateOf(false) }
    var positionLogTestResult by remember { mutableStateOf<String?>(null) }
    var isFetchingEvents by remember { mutableStateOf(false) }
    var recentEventResult by remember { mutableStateOf<String?>(null) }
    var isRunningDemoAction by remember { mutableStateOf(false) }
    var demoActionResult by remember { mutableStateOf<String?>(null) }

    fun runDemoAction(actionName: String, action: suspend () -> String) {
        scope.launch {
            isRunningDemoAction = true
            demoActionResult = null
            Log.d("DebugScreen", "$actionName clicked")

            demoActionResult = try {
                action()
            } catch (e: Exception) {
                "Error: ${e.message ?: e::class.simpleName}"
            }
            Log.d("DebugScreen", "$actionName result: $demoActionResult")
            Toast.makeText(context, demoActionResult, Toast.LENGTH_SHORT).show()
            isRunningDemoAction = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "System",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Connectivity and diagnostics",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                StatusPill(
                    label = when (uiState.dataSourceMode) {
                        DataSourceMode.API_MONGODB -> "MongoDB API Mode"
                        DataSourceMode.LOCAL -> "Local Mode"
                    },
                    color = when (uiState.dataSourceMode) {
                        DataSourceMode.API_MONGODB -> SuccessGreen
                        DataSourceMode.LOCAL -> WarningOrange
                    },
                    icon = when (uiState.dataSourceMode) {
                        DataSourceMode.API_MONGODB -> Icons.Default.Cloud
                        DataSourceMode.LOCAL -> Icons.Default.CloudOff
                    }
                )
            }
        }

        // ── Tracking Detail (if selected) ──────────────────────────────────
        if (uiState.selectedTag != null) {
            SelectedTagDetailPanel(
                selectedTag = uiState.selectedTag,
                anchors = uiState.anchors
            )
        }

        // ── Quick Demo Actions ────────────────────────────────────────────
        SystemCard(
            title = "Quick Demo Actions",
            icon = Icons.Default.Layers,
            subtitle = "Fast access for emulator verification"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SystemButton(
                    text = if (isRunningDemoAction) "Working..." else "Move to Safe Zone",
                    onClick = { runDemoAction("Move to Safe Zone", onMoveAllTagsToSafeZone) },
                    enabled = !isRunningDemoAction,
                    modifier = Modifier.weight(1f),
                    containerColor = SuccessGreen,
                    contentColor = Color.White
                )
                SystemButton(
                    text = if (isRunningDemoAction) "Working..." else "Move to Restricted Zone",
                    onClick = { runDemoAction("Move to Restricted Zone", onMoveAllTagsToRestrictedZone) },
                    enabled = !isRunningDemoAction,
                    modifier = Modifier.weight(1f),
                    containerColor = ErrorRed,
                    contentColor = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SystemButton(
                text = if (isRunningDemoAction) "Working..." else "Randomize Tags",
                onClick = { runDemoAction("Randomize Tags", onRandomizeTagsInsideAnchors) },
                enabled = !isRunningDemoAction,
                containerColor = PrimaryIndigo,
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            )

            when {
                isRunningDemoAction -> {
                    StatusText("Applying demo action...", isError = false)
                }
                demoActionResult != null -> {
                    StatusText(
                        demoActionResult!!,
                        isError = demoActionResult!!.startsWith("No") ||
                            demoActionResult!!.startsWith("At least") ||
                            demoActionResult!!.startsWith("Error")
                    )
                }
            }
        }

        // ── Data Source Card ───────────────────────────────────────────────
        SystemCard(
            title = "Data Source",
            icon = Icons.Default.Storage,
            subtitle = "Switch between local simulation and cloud data"
        ) {
            DetailRow("Mode", uiState.dataSourceMode.label)
            DetailRow("State", if (uiState.isRemoteLoading) "Updating..." else "Stable")
            
            if (uiState.remoteStatusMessage != null) {
                StatusText(uiState.remoteStatusMessage!!, isError = uiState.remoteStatusMessage!!.contains("Failed", true))
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val nextModeLabel = when (uiState.dataSourceMode) {
                DataSourceMode.LOCAL -> "Switch to MongoDB API"
                DataSourceMode.API_MONGODB -> "Switch to Local"
            }
            SystemButton(
                text = nextModeLabel,
                onClick = onToggleDataSourceMode,
                enabled = !uiState.isRemoteLoading,
                isSecondary = true
            )
        }

        // ── Backend API Connectivity ──────────────────────────────────────
        SystemCard(
            title = "Backend API Connectivity",
            icon = Icons.Default.Wifi,
            subtitle = "MongoDB backend API connection status"
        ) {
            DetailRow("Backend", "MongoDB Atlas API")
            DetailRow("API Status", if (backendTestResult?.contains("Connected") == true) "Online" else "Idle")

            if (backendTestResult != null) {
                StatusText(backendTestResult!!, isError = backendTestResult!!.contains("Failed"))
            }

            Spacer(modifier = Modifier.height(12.dp))

            SystemButton(
                text = if (isTestingBackend) "Testing..." else "Test Connection",
                onClick = {
                    scope.launch {
                        isTestingBackend = true
                        backendTestResult = null
                        val backendBaseUrl = BuildConfig.MONGODB_API_BASE_URL
                        try {
                            val apiRepo = com.example.virtualuwb.data.repository.ApiUwbRepository()
                            apiRepo.refreshDevices()
                            val count = apiRepo.getCurrentDevices().size
                            backendTestResult = "Connected (MongoDB). Devices fetched: $count"
                        } catch (e: Exception) {
                            backendTestResult = "Backend unreachable at $backendBaseUrl. Make sure Node.js backend is running. ${e.message ?: e::class.simpleName}"
                        }
                        isTestingBackend = false
                    }
                },
                enabled = !isTestingBackend
            )
        }

        /*
        // ── Position Logging ───────────────────────────────────────────────
        SystemCard(
            title = "Position Logging",
            icon = Icons.Default.MonitorHeart,
            subtitle = "Database synchronization status"
        ) {
            DetailRow("Auto Sync", if (uiState.isPositionLoggingEnabled) "Enabled" else "Disabled")
            DetailRow("Last Event", uiState.lastPositionLogStatus ?: "No events")

            if (positionLogTestResult != null) {
                StatusText(positionLogTestResult!!, isError = !positionLogTestResult!!.startsWith("Inserted"))
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SystemButton(
                    text = if (uiState.isPositionLoggingEnabled) "Disable" else "Enable",
                    onClick = onTogglePositionLogging,
                    isSecondary = true,
                    modifier = Modifier.weight(1f)
                )
                SystemButton(
                    text = if (isTestingPositionLog) "Testing..." else "Test Log",
                    onClick = {
                        scope.launch {
                            isTestingPositionLog = true
                            positionLogTestResult = null
                            val repo = SupabasePositionLogRepository()
                            val pos = uiState.selectedTag?.position ?: GeoPoint(10.762622, 106.660172)
                            try {
                                repo.insertDevicePosition("tag-t1", pos)
                                val count = repo.testConnection("tag-t1").getOrThrow()
                                positionLogTestResult = "Inserted. Logs fetched: $count"
                            } catch (e: Exception) {
                                positionLogTestResult = "Failed: ${e.message ?: e::class.simpleName}"
                            } finally {
                                isTestingPositionLog = false
                            }
                        }
                    },
                    enabled = !isTestingPositionLog,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        */

        // ── Geofence Events ────────────────────────────────────────────────
        SystemCard(
            title = "Geofence Realtime",
            icon = Icons.Default.CompassCalibration,
            subtitle = "Recent ENTER/EXIT events"
        ) {
            DetailRow("Realtime Status", uiState.lastGeofenceEventStatus ?: "Waiting")

            if (recentEventResult != null) {
                StatusText(recentEventResult!!, isError = recentEventResult!!.startsWith("Fetch failed"))
            }

            Spacer(modifier = Modifier.height(12.dp))

            SystemButton(
                text = if (isFetchingEvents) "Fetching..." else "Refresh Recent Events",
                onClick = {
                    scope.launch {
                        isFetchingEvents = true
                        recentEventResult = null
                        try {
                            val repo = com.example.virtualuwb.data.repository.ApiGeofenceEventRepository()
                            val events = repo.getRecentEvents(5)
                            recentEventResult = if (events.isEmpty()) {
                                "No geofence events found"
                            } else {
                                events.take(3).joinToString("\n") { 
                                    "${it.eventType} ${it.deviceName} \u2192 ${it.geofenceName}" 
                                }
                            }
                        } catch (e: Exception) {
                            recentEventResult = "Fetch failed: ${e.message ?: e::class.simpleName}"
                        } finally {
                            isFetchingEvents = false
                        }
                    }
                },
                enabled = !isFetchingEvents,
                icon = Icons.Default.Refresh
            )
        }

        SystemCard(
            title = "Tag movement speed",
            icon = Icons.Default.Power,
            subtitle = "Control automatic tag movement on the Map screen"
        ) {
            DetailRow("Current", tagMovementSpeedLabel(uiState.tagMovementSpeed))

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = uiState.tagMovementSpeed.toFloat(),
                onValueChange = { value -> onTagMovementSpeedChange(value.roundToInt().coerceIn(0, 5)) },
                valueRange = 0f..5f,
                steps = 4
            )

            Text(
                text = "0 pauses automatic tag movement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(136.dp))
    }
}

@Composable
private fun SelectedTagDetailPanel(
    selectedTag: UwbDevice?,
    anchors: List<UwbDevice>,
    modifier: Modifier = Modifier
) {
    if (selectedTag == null) return

    val distances = anchors.map { anchor ->
        anchor to GeoMath.haversineDistanceMeters(anchor.position, selectedTag.position)
    }
    val nearest = distances.minByOrNull { it.second }

    SystemCard(
        title = "Tracking: ${selectedTag.name}",
        icon = Icons.Default.DeviceHub,
        subtitle = "Live proximity tracking"
    ) {
        DetailRow(label = "Position", value = "${formatCoordinate(selectedTag.latitude)}, ${formatCoordinate(selectedTag.longitude)}")

        if (nearest != null) {
            val (nearestAnchor, nearestDistance) = nearest
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = LightBorder)

            DetailRow(label = "Nearest Anchor", value = nearestAnchor.name)
            DetailRow(label = "Distance", value = formatMeters(nearestDistance))
            DetailRow(label = "Proximity", value = distanceStatus(nearestDistance))
        }

        if (distances.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = LightBorder)

            Text(
                text = "Node Distances",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryIndigo
            )
            Spacer(modifier = Modifier.height(4.dp))

            for ((anchor, distance) in distances.take(3)) {
                DetailRow(label = anchor.name, value = formatMeters(distance))
            }
        }
    }
}

@Composable
private fun SystemCard(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, LightBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = PrimaryIndigo.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(modifier = Modifier.padding(8.dp)) {
                        Icon(icon, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (subtitle != null) {
                        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color, icon: ImageVector) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(100.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StatusText(text: String, isError: Boolean) {
    Surface(
        color = (if (isError) ErrorRed else SuccessGreen).copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (isError) Icons.Default.ErrorOutline else Icons.Default.Check,
                contentDescription = null,
                tint = if (isError) ErrorRed else SuccessGreen,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) ErrorRed else SuccessGreen
            )
        }
    }
}

@Composable
private fun SystemButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSecondary: Boolean = false,
    icon: ImageVector? = null,
    containerColor: Color = if (isSecondary) Color.White else PrimaryIndigo,
    contentColor: Color = if (isSecondary) PrimaryIndigo else Color.White,
    borderColor: Color? = if (isSecondary) LightBorder else null
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = borderColor?.let { BorderStroke(1.dp, it) },
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
