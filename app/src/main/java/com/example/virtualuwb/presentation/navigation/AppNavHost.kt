package com.example.virtualuwb.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.virtualuwb.presentation.screen.debug.DebugScreen
import com.example.virtualuwb.presentation.screen.devices.DevicesScreen
import com.example.virtualuwb.presentation.screen.events.EventsScreen
import com.example.virtualuwb.presentation.screen.findmy.FindMyScreen
import com.example.virtualuwb.presentation.screen.geofences.GeofencesScreen
import com.example.virtualuwb.presentation.screen.map.GoogleIndoorMapScreen
import com.example.virtualuwb.presentation.screen.map.MapScreen
import com.example.virtualuwb.presentation.screen.assistant.AssistantScreen
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.collect
import com.example.virtualuwb.data.location.AndroidLocationProvider
import com.example.virtualuwb.presentation.viewmodel.MapUiState
import com.example.virtualuwb.presentation.viewmodel.MapViewModel

@Composable
fun AppNavHost(
    uiState: MapUiState,
    viewModel: MapViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            FloatingAppBottomBar(navController = navController)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Map.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppDestination.Map.route) {
                val context = LocalContext.current
                val locationProvider = remember { AndroidLocationProvider(context) }
                var hasLocationPermission by remember { mutableStateOf(
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
                                            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                }

                LaunchedEffect(Unit) {
                    if (!hasLocationPermission) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }

                LaunchedEffect(hasLocationPermission) {
                    if (hasLocationPermission) {
                        locationProvider.getLocationUpdates().collect { geoPoint ->
                            viewModel.updatePhonePositionFromGps(geoPoint.latitude, geoPoint.longitude)
                        }
                    }
                }

                val useGoogleMaps = true
                if (useGoogleMaps) {
                    GoogleIndoorMapScreen(
                        uiState = uiState,
                        hasLocationPermission = hasLocationPermission,
                        onSelectTagForNavigation = viewModel::selectTagForNavigation,
                        onFetchRoute = viewModel::fetchRouteForSelectedTag
                    )
                } else {
                    MapScreen(
                        uiState = uiState,
                        onToggleSimulation = viewModel::toggleSimulation,
                        onStepSimulation = viewModel::stepSimulation,
                        onResetSimulation = viewModel::resetSimulation,
                        onSelectTag = viewModel::selectTag,
                        onClearTrails = viewModel::clearTrails,
                        onTriggerDemoGeofenceEvent = viewModel::triggerDemoGeofenceEvent
                    )
                }
            }
            composable(AppDestination.Devices.route) {
                DevicesScreen(
                    devices = uiState.devices,
                    onAddDevice = viewModel::addDevice,
                    onUpdateDevice = viewModel::updateDevice,
                    onDeleteDevice = viewModel::deleteDevice
                )
            }
            composable(AppDestination.Geofences.route) {
                GeofencesScreen(
                    geofences = uiState.geofences,
                    onAddGeofence = viewModel::addGeofence,
                    onUpdateGeofence = viewModel::updateGeofence,
                    onDeleteGeofence = viewModel::deleteGeofence,
                    onResetGeofences = viewModel::resetGeofences
                )
            }
            composable(AppDestination.Events.route) {
                EventsScreen(uiState = uiState)
            }
            composable(AppDestination.FindMy.route) {
                FindMyScreen(
                    uiState = uiState,
                    onSelectTag = viewModel::selectTag,
                    onRotatePhoneLeft = viewModel::rotatePhoneLeft,
                    onRotatePhoneRight = viewModel::rotatePhoneRight,
                    onResetPhoneAzimuth = viewModel::resetPhoneAzimuth,
                    onMovePhoneNorth = viewModel::movePhoneNorth,
                    onMovePhoneSouth = viewModel::movePhoneSouth,
                    onMovePhoneEast = viewModel::movePhoneEast,
                    onMovePhoneWest = viewModel::movePhoneWest
                )
            }
            composable(AppDestination.Assistant.route) {
                AssistantScreen(
                    uiState = uiState,
                    mapViewModel = viewModel
                )
            }
            composable(AppDestination.Debug.route) {
                DebugScreen(
                    uiState = uiState,
                    onToggleDataSourceMode = viewModel::toggleDataSourceMode,
                    onTogglePositionLogging = viewModel::togglePositionLogging,
                    onMoveAllTagsToSafeZone = viewModel::moveAllTagsToSafeZone,
                    onMoveAllTagsToRestrictedZone = viewModel::moveAllTagsToRestrictedZone,
                    onRandomizeTagsInsideAnchors = viewModel::randomizeTagsInsideAnchors,
                    onTagMovementSpeedChange = viewModel::setTagMovementSpeed
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FloatingAppBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    var showMoreSheet by rememberSaveable { mutableStateOf(false) }

    val shape = RoundedCornerShape(32.dp)
    val pillBackground = Color(0xFFF7F4FF)
    val borderColor = Color(0xFFE6E0F4)
    val mutedContent = Color(0xFF8B8F9B)

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = pillBackground,
            shadowElevation = 12.dp,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppDestination.items.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    BottomNavItem(
                        destination = screen,
                        selected = selected,
                        activeColor = MaterialTheme.colorScheme.primary,
                        inactiveColor = mutedContent,
                        onClick = { navigateTo(screen.route) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { showMoreSheet = true },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showMoreSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFFFDFBFF)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "More",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Quick access to secondary screens.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedContent
                )

                MoreActionItem(
                    title = AppDestination.Geofences.label,
                    subtitle = "Edit safe and restricted zones",
                    icon = AppDestination.Geofences.icon,
                    onClick = {
                        showMoreSheet = false
                        navigateTo(AppDestination.Geofences.route)
                    }
                )

                MoreActionItem(
                    title = AppDestination.Debug.label,
                    subtitle = "System tools and demo controls",
                    icon = AppDestination.Debug.icon,
                    onClick = {
                        showMoreSheet = false
                        navigateTo(AppDestination.Debug.route)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    destination: AppDestination,
    selected: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconTint by animateColorAsState(
        targetValue = if (selected) activeColor else inactiveColor,
        label = "${destination.route}-icon-tint"
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.72f,
        label = "${destination.route}-label-alpha"
    )
    val highlightScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.88f,
        label = "${destination.route}-highlight-scale"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.size(42.dp),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(34.dp * highlightScale)
                        .clip(CircleShape)
                        .background(activeColor.copy(alpha = 0.14f))
                )
            }
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
                tint = iconTint
            )
        }
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = iconTint.copy(alpha = labelAlpha)
        )
    }
}

@Composable
private fun MoreActionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, Color(0xFFE7E3EF))
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8B8F9B)
                )
            },
            leadingContent = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        )
    }
}
