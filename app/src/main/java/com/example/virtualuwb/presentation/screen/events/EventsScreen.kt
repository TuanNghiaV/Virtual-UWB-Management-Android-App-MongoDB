package com.example.virtualuwb.presentation.screen.events

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import com.example.virtualuwb.data.repository.ApiGeofenceEventRepository
import com.example.virtualuwb.data.repository.ApiGeofenceEventStreamRepository
import com.example.virtualuwb.domain.model.GeofenceEvent
import com.example.virtualuwb.domain.model.GeofenceEventType
import com.example.virtualuwb.domain.model.GeofenceType
import com.example.virtualuwb.domain.model.DataSourceMode
import com.example.virtualuwb.domain.repository.GeofenceEventRepository
import com.example.virtualuwb.presentation.viewmodel.MapUiState
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class EventFilter {
    ALL,
    ENTER,
    EXIT,
    RESTRICTED
}

private val EnterContainer = Color(0xFFE7F7EE)
private val EnterContent = Color(0xFF1B5E20)
private val ExitContainer = Color(0xFFFFF3E0)
private val ExitContent = Color(0xFFE65100)
private val DwellContainer = Color(0xFFEFF1F5)
private val DwellContent = Color(0xFF5F6368)
private val RestrictedContainer = Color(0xFFFFCDD2)
private val RestrictedContent = Color(0xFFB71C1C)
private val CardBackground = Color(0xFFFFFFFF)
private val LightBorder = Color(0xFFE5E7EB)

private val CardShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(50.dp)
private val AccentStripShape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)

private const val EVENTS_FETCH_LIMIT = 200

private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

private val EventTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

private fun formatEventTime(value: String?): String {
    if (value == null) return "Unknown time"

    return runCatching {
        OffsetDateTime.parse(value)
            .toInstant()
            .atZone(ZoneId.systemDefault())
            .format(EventTimeFormatter)
    }.getOrElse {
        value
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EventsScreen(
    uiState: MapUiState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val repo: GeofenceEventRepository = remember { ApiGeofenceEventRepository() }
    val apiRealtimeRepo = remember { ApiGeofenceEventStreamRepository() }
    val pullToRefreshState = rememberPullToRefreshState()

    var events by remember { mutableStateOf<List<GeofenceEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var totalEventCount by remember { mutableStateOf<Int?>(null) }
    var selectedFilter by rememberSaveable { mutableStateOf(EventFilter.ALL.name) }
    var realtimeStatus by remember { mutableStateOf("Realtime: connecting...") }
    var showFilterSheet by remember { mutableStateOf(false) }

    val fetchEvents: suspend () -> Unit = {
        isLoading = true
        errorMessage = null
        try {
            events = repo.getRecentEvents(EVENTS_FETCH_LIMIT)
        } catch (e: Exception) {
            errorMessage = e.message ?: e::class.simpleName
            totalEventCount = null
        } finally {
            isLoading = false
        }

        try {
            totalEventCount = events.size
        } catch (_: Exception) {
            totalEventCount = null
        }
    }

    LaunchedEffect(repo) {
        fetchEvents()
    }

    LaunchedEffect(uiState.dataSourceMode) {
        try {
            realtimeStatus = "Realtime: connecting..."
            apiRealtimeRepo.geofenceEventsFlow().collect { newEvent ->
                realtimeStatus = "Realtime: active"
                
                val isDuplicate = events.any { existing ->
                    (existing.id != null && newEvent.id != null && existing.id == newEvent.id) ||
                    (existing.deviceId == newEvent.deviceId && existing.createdAt == newEvent.createdAt && existing.eventType == newEvent.eventType)
                }
                
                if (!isDuplicate) {
                    events = listOf(newEvent) + events
                    totalEventCount = events.size
                }
            }
        } catch (e: Exception) {
            realtimeStatus = "Realtime failed: ${e.message ?: e::class.simpleName}"
        }
    }

    val activeFilter = EventFilter.valueOf(selectedFilter)
    val filteredEvents = when (activeFilter) {
        EventFilter.ALL -> events
        EventFilter.ENTER -> events.filter { it.eventType == GeofenceEventType.ENTER }
        EventFilter.EXIT -> events.filter { it.eventType == GeofenceEventType.EXIT }
        EventFilter.RESTRICTED -> events.filter { it.geofenceType == GeofenceType.RESTRICTED_ZONE }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false }
        ) {
            EventsFilterSheet(
                selectedFilter = selectedFilter,
                onSelect = {
                    selectedFilter = it
                    showFilterSheet = false
                }
            )
        }
    }

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = isLoading,
        onRefresh = { scope.launch { fetchEvents() } },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 128.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Events",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Realtime geofence activity",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                val countLabel = totalEventCount?.let { totalCount ->
                    "Showing: ${events.size} / $totalCount"
                } ?: "Showing: ${events.size}"

                EventsSummaryToolbar(
                    countLabel = countLabel,
                    selectedFilter = filterLabel(EventFilter.valueOf(selectedFilter)),
                    onOpenFilters = { showFilterSheet = true }
                )
            }

            when {
                errorMessage != null -> {
                    item {
                        StatusCard(
                            message = errorMessage ?: "Unknown error",
                            isError = true
                        )
                    }
                }

                filteredEvents.isEmpty() -> {
                    item {
                        StatusCard(
                            message = "No events yet",
                            isError = false
                        )
                    }
                }

                else -> {
                    items(filteredEvents) { event ->
                        EventCard(event = event)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventsSummaryToolbar(
    countLabel: String,
    selectedFilter: String,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, LightBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = countLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        label = selectedFilter,
                        selected = true,
                        onClick = onOpenFilters
                    )
                    IconButton(onClick = onOpenFilters) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = "Filter"
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pull down to refresh",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EventsFilterSheet(
    selectedFilter: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Filter events",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Choose which event type to display",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        EventFilter.values().forEach { filter ->
            val isSelected = selectedFilter == filter.name
            FilterSheetRow(
                label = filterLabel(filter),
                description = filterDescription(filter),
                selected = isSelected,
                onClick = { onSelect(filter.name) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun FilterSheetRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun EventCard(
    event: GeofenceEvent,
    modifier: Modifier = Modifier
) {
    val accentColor = eventAccentColor(event)
    val (eventContainer, eventContent) = eventBadgeColors(event)
    val (typeContainer, typeContent) = geofenceChipColors(event.geofenceType)
    val actionText = eventSentence(event)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = CardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, LightBorder)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(AccentStripShape)
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        text = event.eventType.name,
                        containerColor = eventContainer,
                        contentColor = eventContent
                    )
                    Text(
                        text = formatEventTime(event.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = actionText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        text = event.geofenceType.name.replace("_", " "),
                        containerColor = typeContainer,
                        contentColor = typeContent
                    )
                    Text(
                        text = "Lat/Lon: ${formatCoordinate(event.position.latitude)}, ${formatCoordinate(event.position.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = PillShape
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    Surface(
        modifier = modifier
            .height(34.dp)
            .clip(PillShape)
            .clickable { onClick() },
        color = containerColor,
        contentColor = contentColor,
        border = border,
        shape = PillShape
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatusCard(
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = contentColor
        )
    }
}

private fun eventAccentColor(event: GeofenceEvent): Color = when (event.eventType) {
    GeofenceEventType.ENTER -> EnterContent
    GeofenceEventType.EXIT -> ExitContent
    GeofenceEventType.DWELL -> DwellContent
}

private fun eventBadgeColors(event: GeofenceEvent): Pair<Color, Color> = when (event.eventType) {
    GeofenceEventType.ENTER -> EnterContainer to EnterContent
    GeofenceEventType.EXIT -> ExitContainer to ExitContent
    GeofenceEventType.DWELL -> DwellContainer to DwellContent
}

private fun geofenceChipColors(type: GeofenceType): Pair<Color, Color> = when (type) {
    GeofenceType.RESTRICTED_ZONE -> RestrictedContainer to RestrictedContent
    GeofenceType.SAFE_ZONE -> EnterContainer to EnterContent
    GeofenceType.ROOM -> DwellContainer to DwellContent
}

private fun eventSentence(event: GeofenceEvent): String = when (event.eventType) {
    GeofenceEventType.ENTER -> "${event.deviceName} entered ${event.geofenceName}"
    GeofenceEventType.EXIT -> "${event.deviceName} exited ${event.geofenceName}"
    GeofenceEventType.DWELL -> "${event.deviceName} stayed in ${event.geofenceName}"
}

private fun filterLabel(filter: EventFilter): String =
    when (filter) {
        EventFilter.ALL -> "All"
        EventFilter.ENTER -> "Enter"
        EventFilter.EXIT -> "Exit"
        EventFilter.RESTRICTED -> "Restricted"
    }

private fun filterDescription(filter: EventFilter): String =
    when (filter) {
        EventFilter.ALL -> "All event types"
        EventFilter.ENTER -> "Only ENTER events"
        EventFilter.EXIT -> "Only EXIT events"
        EventFilter.RESTRICTED -> "Events inside restricted zones"
    }
