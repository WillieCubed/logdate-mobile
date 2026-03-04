@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.location.timeline.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.feature.location.timeline.ui.model.LocationTimelineItem
import app.logdate.feature.location.timeline.ui.model.LocationTimelineUiState
import logdate.client.feature.location.timeline.generated.resources.*
import logdate.client.feature.location.timeline.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationTimelineScreen(
    modifier: Modifier = Modifier,
    viewModel: LocationTimelineViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.location_timeline),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        LocationTimelineContent(
            uiState = uiState,
            onDeleteLocation = viewModel::deleteLocationEntry,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        )
    }
}

@Composable
fun LocationTimelineContent(
    uiState: LocationTimelineUiState,
    onDeleteLocation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is LocationTimelineUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        is LocationTimelineUiState.Error -> {
            Column(
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.unable_to_load_location_timeline),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is LocationTimelineUiState.Success -> {
            if (uiState.allLocations.isEmpty()) {
                EmptyLocationTimeline(modifier = modifier.fillMaxWidth())
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Display all locations in chronological order
                    items(
                        items = uiState.allLocations,
                        key = { it.id },
                    ) { locationItem ->
                        if (locationItem.isCurrentLocation) {
                            // Current location card
                            LocationCard(
                                locationItem = locationItem,
                                isCurrentLocation = true,
                                onDelete = null, // Don't allow deleting current location
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            // History location card
                            LocationCard(
                                locationItem = locationItem,
                                isCurrentLocation = false,
                                onDelete = { onDeleteLocation(locationItem.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationCard(
    locationItem: LocationTimelineItem,
    isCurrentLocation: Boolean,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Different styling for current vs historical locations
    val cardColors =
        if (isCurrentLocation) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            )
        }

    Card(
        modifier = modifier,
        colors = cardColors,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon is different for current vs historical locations
            val icon = if (isCurrentLocation) Icons.Default.GpsFixed else Icons.Default.LocationOn
            val iconTint =
                if (isCurrentLocation) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            val iconSize = if (isCurrentLocation) 24.dp else 20.dp

            Icon(
                imageVector = icon,
                contentDescription = if (isCurrentLocation) "Current location" else "Location",
                modifier = Modifier.size(iconSize),
                tint = iconTint,
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Current location label
                if (isCurrentLocation) {
                    Text(
                        text = stringResource(Res.string.current_location),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color =
                            if (isCurrentLocation) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }

                // Location name
                Text(
                    text = locationItem.placeName,
                    style =
                        if (isCurrentLocation) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.titleSmall
                        },
                    fontWeight = if (isCurrentLocation) FontWeight.SemiBold else FontWeight.Medium,
                )

                // Address
                if (locationItem.address.isNotBlank()) {
                    Text(
                        text = locationItem.address,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (isCurrentLocation) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }

                // Always show coordinates for current location
                if (isCurrentLocation) {
                    Text(
                        text =
                            stringResource(
                                Res.string.coordinates_label,
                                formatCoordinates(locationItem.latitude, locationItem.longitude),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }

                // Time ago
                Text(
                    text = locationItem.timeAgo,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (isCurrentLocation) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        },
                )

                // Duration (if available)
                if (locationItem.duration != null) {
                    Text(
                        text =
                            stringResource(
                                Res.string.stayed_for_duration,
                                locationItem.duration,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (isCurrentLocation) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                    )
                }
            }

            // Delete button - only for history items
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.delete_location),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLocationTimeline(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(Res.string.no_location_history_yet),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.your_location_timeline_will_appear_here_as_you_move_around),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

/**
 * Formats coordinates to display with 6 decimal places
 */
private fun formatCoordinates(
    latitude: Double,
    longitude: Double,
): String = "${formatCoordinateValue(latitude)}, ${formatCoordinateValue(longitude)}"
