package com.example.virtualuwb.presentation.screen.devices

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Tag
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
import com.example.virtualuwb.domain.model.UwbDevice
import com.example.virtualuwb.domain.model.UwbRole
import java.util.Locale

private val AnchorColor = Color(0xFF2962FF)
private val TagColor = Color(0xFF7C4DFF)
private val LightBorder = Color(0xFFE5E7EB)
private val ScreenBackground = Color(0xFFF9FAFB)

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

@Composable
fun DevicesScreen(
    devices: List<UwbDevice>,
    onAddDevice: (UwbDevice) -> Unit,
    onUpdateDevice: (UwbDevice) -> Unit,
    onDeleteDevice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var filterRole by rememberSaveable { mutableStateOf<String?>(null) } // null = All, else UwbRole name
    var isAddDialogVisible by remember { mutableStateOf(false) }
    var editingDevice by remember { mutableStateOf<UwbDevice?>(null) }
    var devicePendingDelete by remember { mutableStateOf<UwbDevice?>(null) }
    var isFilterSheetVisible by remember { mutableStateOf(false) }

    val filteredDevices = when (filterRole) {
        null -> devices
        else -> devices.filter { it.role.name == filterRole }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScreenBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAddDialogVisible = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Device")
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
            Column {
                Text(
                    text = "Devices",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage anchors and mobile tags",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Summary Section ─────────────────────────────────────────────────
            SummarySection(devices = devices)

            // ── List Filters ────────────────────────────────────────────────────
            CompactFilterSection(
                selectedRole = filterRole,
                onClick = { isFilterSheetVisible = true }
            )

            // ── Device List ─────────────────────────────────────────────────────
            if (filteredDevices.isEmpty()) {
                EmptyState()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    filteredDevices.forEach { device ->
                        DeviceCard(
                            device = device,
                            onEdit = { editingDevice = device },
                            onDelete = { devicePendingDelete = device }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp)) // Padding for FAB
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────

    if (isAddDialogVisible) {
        DeviceFormDialog(
            title = "Add Device",
            confirmLabel = "Add Device",
            onDismiss = { isAddDialogVisible = false },
            onSubmit = { newDevice ->
                onAddDevice(newDevice)
                isAddDialogVisible = false
            }
        )
    }

    editingDevice?.let { device ->
        DeviceFormDialog(
            title = "Edit Device",
            confirmLabel = "Save Changes",
            initialDevice = device,
            onDismiss = { editingDevice = null },
            onSubmit = { updatedDevice ->
                onUpdateDevice(updatedDevice)
                editingDevice = null
            }
        )
    }

    devicePendingDelete?.let { device ->
        DeleteDeviceDialog(
            deviceName = device.name,
            onDismiss = { devicePendingDelete = null },
            onConfirm = {
                onDeleteDevice(device.id)
                devicePendingDelete = null
            }
        )
    }

    if (isFilterSheetVisible) {
        DeviceFilterBottomSheet(
            selectedRole = filterRole,
            onSelect = { role ->
                filterRole = role
                isFilterSheetVisible = false
            },
            onDismiss = { isFilterSheetVisible = false }
        )
    }
}

@Composable
private fun SummarySection(devices: List<UwbDevice>) {
    val anchors = devices.count { it.role == UwbRole.ANCHOR }
    val tags = devices.count { it.role == UwbRole.TAG }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryChip(label = "Total", value = "${devices.size}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        SummaryChip(label = "Anchors", value = "$anchors", color = AnchorColor, modifier = Modifier.weight(1f))
        SummaryChip(label = "Tags", value = "$tags", color = TagColor, modifier = Modifier.weight(1f))
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
private fun CompactFilterSection(selectedRole: String?, onClick: () -> Unit) {
    val currentLabel = when (selectedRole) {
        null -> "All"
        UwbRole.ANCHOR.name -> "Anchors"
        UwbRole.TAG.name -> "Tags"
        else -> "All"
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
private fun DeviceFilterBottomSheet(
    selectedRole: String?,
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
                Text(text = "Filter devices", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(text = "Choose which devices to show", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))

            FilterOptionRow(
                label = "All",
                description = "Show all devices",
                dotColor = MaterialTheme.colorScheme.primary,
                selected = selectedRole == null,
                onClick = { onSelect(null) }
            )
            FilterOptionRow(
                label = "Anchors",
                description = "Fixed reference anchors",
                dotColor = AnchorColor,
                selected = selectedRole == UwbRole.ANCHOR.name,
                onClick = { onSelect(UwbRole.ANCHOR.name) }
            )
            FilterOptionRow(
                label = "Tags",
                description = "Mobile tracked tags",
                dotColor = TagColor,
                selected = selectedRole == UwbRole.TAG.name,
                onClick = { onSelect(UwbRole.TAG.name) }
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
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "No devices found",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Add an anchor or tag to get started",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun DeviceFormDialog(
    title: String,
    confirmLabel: String,
    initialDevice: UwbDevice? = null,
    onDismiss: () -> Unit,
    onSubmit: (UwbDevice) -> Unit
) {
    var name by remember { mutableStateOf(initialDevice?.name ?: "") }
    var code by remember { mutableStateOf(initialDevice?.id ?: "") }
    var role by remember { mutableStateOf(initialDevice?.role ?: UwbRole.TAG) }
    var lat by remember { mutableStateOf(initialDevice?.latitude?.toString() ?: "") }
    var lon by remember { mutableStateOf(initialDevice?.longitude?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    val isEditMode = initialDevice != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Device Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = { if (!isEditMode) code = it },
                    label = { Text("Device ID/Code") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    readOnly = isEditMode,
                    enabled = !isEditMode
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoleOption(
                        label = "ANCHOR",
                        selected = role == UwbRole.ANCHOR,
                        color = AnchorColor,
                        onClick = { role = UwbRole.ANCHOR },
                        modifier = Modifier.weight(1f)
                    )
                    RoleOption(
                        label = "TAG",
                        selected = role == UwbRole.TAG,
                        color = TagColor,
                        onClick = { role = UwbRole.TAG },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = lat,
                        onValueChange = { lat = it },
                        label = { Text("Lat") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = lon,
                        onValueChange = { lon = it },
                        label = { Text("Lon") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = name.trim()
                    val finalCode = code.trim()
                    if (finalName.isEmpty() || finalCode.isEmpty()) {
                        error = "Name and Code are required"
                        return@Button
                    }
                    val latVal = lat.toDoubleOrNull()
                    val lonVal = lon.toDoubleOrNull()
                    if (latVal == null || lonVal == null || !GeoPoint.isValid(latVal, lonVal)) {
                        error = "Invalid coordinates"
                        return@Button
                    }

                    val device = UwbDevice(
                        id = finalCode,
                        name = finalName,
                        role = role,
                        position = GeoPoint(latVal, lonVal)
                    )
                    onSubmit(device)
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
private fun DeleteDeviceDialog(
    deviceName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete device?", fontWeight = FontWeight.Bold) },
        text = {
            Text("Are you sure you want to delete $deviceName? This action will remove it from the active device list.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Delete")
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
private fun RoleOption(
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
        Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun DeviceCard(
    device: UwbDevice,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isAnchor = device.role == UwbRole.ANCHOR
    val roleColor = if (isAnchor) AnchorColor else TagColor
    val roleIcon = if (isAnchor) Icons.Default.Router else Icons.Default.Tag

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
                        color = roleColor.copy(alpha = 0.1f),
                        contentColor = roleColor,
                        shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(imageVector = roleIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = device.id,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Surface(
                    color = roleColor.copy(alpha = 0.1f),
                    contentColor = roleColor,
                    shape = CircleShape
                ) {
                    Text(
                        text = device.role.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${formatCoordinate(device.latitude)}, ${formatCoordinate(device.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
