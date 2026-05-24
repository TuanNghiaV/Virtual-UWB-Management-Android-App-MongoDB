package com.example.virtualuwb.presentation.screen.geofences

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.virtualuwb.domain.model.GeoPoint
import com.example.virtualuwb.domain.model.Geofence
import com.example.virtualuwb.domain.model.GeofenceType
import java.util.Locale

private val RestrictedZoneColor = Color(0xFFEF4444) // Red
private val SafeZoneColor = Color(0xFF10B981) // Green
private val RoomColor = Color(0xFF6B7280) // Gray
private val LightBorder = Color(0xFFE5E7EB)
private val ScreenBackground = Color(0xFFF9FAFB)

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

@Composable
fun GeofencesScreen(
    geofences: List<Geofence>,
    onAddGeofence: (Geofence) -> Unit,
    onUpdateGeofence: (Geofence) -> Unit,
    onDeleteGeofence: (String) -> Unit,
    onResetGeofences: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filterType by rememberSaveable { mutableStateOf<String?>(null) } // null = All
    var isAddDialogVisible by remember { mutableStateOf(false) }
    var editingGeofence by remember { mutableStateOf<Geofence?>(null) }
    var geofencePendingDelete by remember { mutableStateOf<Geofence?>(null) }
    var isResetConfirmVisible by remember { mutableStateOf(false) }
    var isFilterSheetVisible by remember { mutableStateOf(false) }

    val filteredGeofences = when (filterType) {
        null -> geofences
        else -> geofences.filter { it.type.name == filterType }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScreenBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAddDialogVisible = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Geofence")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Geofences",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Local polygon zones and safety areas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(
                    onClick = { isResetConfirmVisible = true },
                    modifier = Modifier.background(Color.White, CircleShape).size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Defaults",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ── Summary Section ─────────────────────────────────────────────────
            SummarySection(geofences = geofences)

            // ── List Filters ────────────────────────────────────────────────────
            CompactFilterSection(
                selectedType = filterType,
                onClick = { isFilterSheetVisible = true }
            )

            // ── Geofence List ─────────────────────────────────────────────────────
            if (filteredGeofences.isEmpty()) {
                EmptyState()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    filteredGeofences.forEach { geofence ->
                        GeofenceCard(
                            geofence = geofence,
                            onEdit = { editingGeofence = geofence },
                            onDelete = { geofencePendingDelete = geofence }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp)) // Padding for FAB
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────

    if (isAddDialogVisible) {
        GeofenceFormDialog(
            title = "Add Geofence",
            confirmLabel = "Add Geofence",
            onDismiss = { isAddDialogVisible = false },
            onSubmit = { newGeofence ->
                onAddGeofence(newGeofence)
                isAddDialogVisible = false
            }
        )
    }

    editingGeofence?.let { geofence ->
        GeofenceFormDialog(
            title = "Edit Geofence",
            confirmLabel = "Save Changes",
            initialGeofence = geofence,
            onDismiss = { editingGeofence = null },
            onSubmit = { updatedGeofence ->
                onUpdateGeofence(updatedGeofence)
                editingGeofence = null
            }
        )
    }

    geofencePendingDelete?.let { geofence ->
        DeleteGeofenceDialog(
            geofenceName = geofence.name,
            onDismiss = { geofencePendingDelete = null },
            onConfirm = {
                onDeleteGeofence(geofence.id)
                geofencePendingDelete = null
            }
        )
    }

    if (isResetConfirmVisible) {
        ResetGeofencesDialog(
            onDismiss = { isResetConfirmVisible = false },
            onConfirm = {
                onResetGeofences()
                isResetConfirmVisible = false
            }
        )
    }

    if (isFilterSheetVisible) {
        GeofenceFilterBottomSheet(
            selectedType = filterType,
            onSelect = { type ->
                filterType = type
                isFilterSheetVisible = false
            },
            onDismiss = { isFilterSheetVisible = false }
        )
    }
}

@Composable
private fun SummarySection(geofences: List<Geofence>) {
    val restricted = geofences.count { it.type == GeofenceType.RESTRICTED_ZONE }
    val safe = geofences.count { it.type == GeofenceType.SAFE_ZONE }
    val rooms = geofences.count { it.type == GeofenceType.ROOM }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryChip(label = "Total", value = "${geofences.size}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        SummaryChip(label = "Restricted", value = "$restricted", color = RestrictedZoneColor, modifier = Modifier.weight(1.5f))
        SummaryChip(label = "Safe/Rooms", value = "${safe + rooms}", color = SafeZoneColor, modifier = Modifier.weight(1.5f))
    }
}

@Composable
private fun SummaryChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.08f),
        contentColor = color,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun CompactFilterSection(selectedType: String?, onClick: () -> Unit) {
    val currentLabel = when (selectedType) {
        null -> "All Zones"
        GeofenceType.ROOM.name -> "Rooms"
        GeofenceType.SAFE_ZONE.name -> "Safe Zones"
        GeofenceType.RESTRICTED_ZONE.name -> "Restricted"
        else -> "All Zones"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick() },
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            contentColor = MaterialTheme.colorScheme.primary,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(text = currentLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeofenceFilterBottomSheet(
    selectedType: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        dragHandle = { Box(modifier = Modifier.padding(top = 12.dp).width(32.dp).height(4.dp).clip(CircleShape).background(LightBorder)) },
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(text = "Filter zones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(text = "Choose which geofence types to show", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))

            FilterOptionRow(
                label = "All Zones",
                description = "Show all geofence areas",
                dotColor = MaterialTheme.colorScheme.primary,
                selected = selectedType == null,
                onClick = { onSelect(null) }
            )
            FilterOptionRow(
                label = "Rooms",
                description = "Internal building partitions",
                dotColor = RoomColor,
                selected = selectedType == GeofenceType.ROOM.name,
                onClick = { onSelect(GeofenceType.ROOM.name) }
            )
            FilterOptionRow(
                label = "Safe Zones",
                description = "Permitted operation areas",
                dotColor = SafeZoneColor,
                selected = selectedType == GeofenceType.SAFE_ZONE.name,
                onClick = { onSelect(GeofenceType.SAFE_ZONE.name) }
            )
            FilterOptionRow(
                label = "Restricted Zones",
                description = "Prohibited or warning areas",
                dotColor = RestrictedZoneColor,
                selected = selectedType == GeofenceType.RESTRICTED_ZONE.name,
                onClick = { onSelect(GeofenceType.RESTRICTED_ZONE.name) }
            )
        }
    }
}

@Composable
private fun FilterOptionRow(
    label: String,
    description: String,
    dotColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                Text(text = description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (selected) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun GeofenceCard(
    geofence: Geofence,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val typeColor = when (geofence.type) {
        GeofenceType.RESTRICTED_ZONE -> RestrictedZoneColor
        GeofenceType.SAFE_ZONE -> SafeZoneColor
        GeofenceType.ROOM -> RoomColor
    }

    val minLat = geofence.vertices.minOf { it.latitude }
    val maxLat = geofence.vertices.maxOf { it.latitude }
    val minLon = geofence.vertices.minOf { it.longitude }
    val maxLon = geofence.vertices.maxOf { it.longitude }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, LightBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        color = typeColor.copy(alpha = 0.1f),
                        contentColor = typeColor,
                        shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column {
                        Text(
                            text = geofence.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = geofence.id,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Surface(
                    color = typeColor.copy(alpha = 0.1f),
                    contentColor = typeColor,
                    shape = CircleShape
                ) {
                    Text(
                        text = geofence.type.name.replace("_", " "),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "Lat: ${formatCoordinate(minLat)} to ${formatCoordinate(maxLat)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Lon: ${formatCoordinate(minLon)} to ${formatCoordinate(maxLon)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun GeofenceFormDialog(
    title: String,
    confirmLabel: String,
    initialGeofence: Geofence? = null,
    onDismiss: () -> Unit,
    onSubmit: (Geofence) -> Unit
) {
    var name by remember { mutableStateOf(initialGeofence?.name ?: "") }
    var id by remember { mutableStateOf(initialGeofence?.id ?: "") }
    var type by remember { mutableStateOf(initialGeofence?.type ?: GeofenceType.RESTRICTED_ZONE) }
    
    val initialMinLat = initialGeofence?.vertices?.minOf { it.latitude }?.let { formatCoordinate(it) } ?: ""
    val initialMaxLat = initialGeofence?.vertices?.maxOf { it.latitude }?.let { formatCoordinate(it) } ?: ""
    val initialMinLon = initialGeofence?.vertices?.minOf { it.longitude }?.let { formatCoordinate(it) } ?: ""
    val initialMaxLon = initialGeofence?.vertices?.maxOf { it.longitude }?.let { formatCoordinate(it) } ?: ""

    var minLat by remember { mutableStateOf(initialMinLat) }
    var maxLat by remember { mutableStateOf(initialMaxLat) }
    var minLon by remember { mutableStateOf(initialMinLon) }
    var maxLon by remember { mutableStateOf(initialMaxLon) }
    var error by remember { mutableStateOf<String?>(null) }

    val isEditMode = initialGeofence != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Geofence Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = id,
                    onValueChange = { if (!isEditMode) id = it },
                    label = { Text("Geofence ID/Code") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    readOnly = isEditMode,
                    enabled = !isEditMode
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Zone Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        GeofenceTypeOption(
                            label = "ROOM",
                            selected = type == GeofenceType.ROOM,
                            color = RoomColor,
                            onClick = { type = GeofenceType.ROOM },
                            modifier = Modifier.weight(1f)
                        )
                        GeofenceTypeOption(
                            label = "SAFE",
                            selected = type == GeofenceType.SAFE_ZONE,
                            color = SafeZoneColor,
                            onClick = { type = GeofenceType.SAFE_ZONE },
                            modifier = Modifier.weight(1f)
                        )
                        GeofenceTypeOption(
                            label = "RESTRICTED",
                            selected = type == GeofenceType.RESTRICTED_ZONE,
                            color = RestrictedZoneColor,
                            onClick = { type = GeofenceType.RESTRICTED_ZONE },
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = minLat,
                        onValueChange = { minLat = it },
                        label = { Text("Min Lat") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = maxLat,
                        onValueChange = { maxLat = it },
                        label = { Text("Max Lat") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = minLon,
                        onValueChange = { minLon = it },
                        label = { Text("Min Lon") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = maxLon,
                        onValueChange = { maxLon = it },
                        label = { Text("Max Lon") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = name.trim()
                    val finalId = id.trim()
                    if (finalName.isEmpty() || finalId.isEmpty()) {
                        error = "Name and ID are required"
                        return@Button
                    }
                    val lat1 = minLat.toDoubleOrNull()
                    val lat2 = maxLat.toDoubleOrNull()
                    val lon1 = minLon.toDoubleOrNull()
                    val lon2 = maxLon.toDoubleOrNull()

                    if (lat1 == null || lat2 == null || lon1 == null || lon2 == null) {
                        error = "Invalid coordinates"
                        return@Button
                    }
                    if (lat1 >= lat2 || lon1 >= lon2) {
                        error = "Min must be less than Max"
                        return@Button
                    }
                    if (!GeoPoint.isValid(lat1, lon1) || !GeoPoint.isValid(lat2, lon2)) {
                        error = "Coordinates out of range"
                        return@Button
                    }

                    val vertices = listOf(
                        GeoPoint(lat2, lon1), GeoPoint(lat2, lon2),
                        GeoPoint(lat1, lon2), GeoPoint(lat1, lon1)
                    )
                    val geofence = Geofence(finalId, finalName, type, vertices)
                    onSubmit(geofence)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
private fun GeofenceTypeOption(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = if (selected) color.copy(alpha = 0.1f) else Color.Transparent,
        contentColor = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, if (selected) color.copy(alpha = 0.5f) else LightBorder)
    ) {
        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 9.sp)
        }
    }
}

@Composable
private fun DeleteGeofenceDialog(geofenceName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete geofence?", fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to delete $geofenceName? This action will remove it from the active geofence list.") },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), shape = RoundedCornerShape(12.dp)) {
                Text("Delete")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
private fun ResetGeofencesDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset geofences?", fontWeight = FontWeight.Bold) },
        text = { Text("This will restore the default geofence set. All custom zones will be lost.") },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(12.dp)) {
                Text("Reset")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "No geofences found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = "Add a room or restricted zone to get started", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}
