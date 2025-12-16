package app.logdate.feature.location.timeline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.feature.location.timeline.ui.model.LocationTimelineItem
import app.logdate.feature.location.timeline.ui.model.LocationTimelineUiState
import app.logdate.client.permissions.rememberLocationPermissionState
import app.logdate.ui.theme.Spacing

/**
 * A component that displays the user's location history as a list.
 * Optimized for both full screen and bottom sheet contexts.
 *
 * @param uiState The current state of the location timeline
 * @param onDeleteLocation Callback for when the user deletes a location entry
 */
@Composable
fun LocationTimelineView(
    uiState: LocationTimelineUiState,
    onDeleteLocation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val locationPermissionState = rememberLocationPermissionState()
    when (uiState) {
        is LocationTimelineUiState.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        is LocationTimelineUiState.Error -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Unable to load location timeline",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        is LocationTimelineUiState.Success -> {
            LazyColumn(
                modifier = modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Current location section - show permission affordance if no current location due to permissions
                item {
                    if (uiState.currentLocation != null) {
                        // Show actual current location
                        LocationItemCard(
                            locationItem = uiState.currentLocation,
                            onDelete = null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (!locationPermissionState.hasPermission) {
                        // Show permission affordance for current location
                        CurrentLocationPermissionCard(
                            onRequestPermission = locationPermissionState.requestPermission,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Location history items
                if (uiState.locationHistory.isEmpty()) {
                    item {
                        EmptyLocationHistoryCard(modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    items(
                        items = uiState.locationHistory,
                        key = { it.id }
                    ) { locationItem ->
                        LocationItemCard(
                            locationItem = locationItem,
                            onDelete = { onDeleteLocation(locationItem.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * A card displaying a location item with optional delete functionality
 */
@Composable
private fun LocationItemCard(
    locationItem: LocationTimelineItem,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null
) {
    val isCurrentLocation = locationItem.isCurrentLocation
    
    // Different styling for current vs historical locations
    val cardColors = if (isCurrentLocation) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    }
    
    Card(
        modifier = modifier,
        colors = cardColors,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon is different for current vs historical locations
            val icon = if (isCurrentLocation) Icons.Default.GpsFixed else Icons.Default.LocationOn
            val iconTint = if (isCurrentLocation) MaterialTheme.colorScheme.primary 
                          else MaterialTheme.colorScheme.onSurfaceVariant
            val iconSize = if (isCurrentLocation) 24.dp else 20.dp
            
            Icon(
                imageVector = icon,
                contentDescription = if (isCurrentLocation) "Current location" else "Location",
                modifier = Modifier.size(iconSize),
                tint = iconTint
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // Current location label
                if (isCurrentLocation) {
                    Text(
                        text = "Current Location",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isCurrentLocation) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Location name
                Text(
                    text = locationItem.placeName,
                    style = if (isCurrentLocation) MaterialTheme.typography.titleMedium 
                            else MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrentLocation) FontWeight.SemiBold else FontWeight.Medium
                )
                
                // Address
                if (locationItem.address.isNotBlank()) {
                    Text(
                        text = locationItem.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCurrentLocation) 
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Always show coordinates for current location
                if (isCurrentLocation) {
                    Text(
                        text = "Coordinates: ${formatCoordinates(locationItem.latitude, locationItem.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                
                // Time ago
                Text(
                    text = locationItem.timeAgo,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrentLocation)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                
                // Duration (if available)
                if (locationItem.duration != null) {
                    Text(
                        text = "Stayed for ${locationItem.duration}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCurrentLocation)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            
            // Delete button - only for history items
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete location",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Permission affordance card for current location access
 */
@Composable
private fun CurrentLocationPermissionCard(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Current location unavailable",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Enable location access to see your current location",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            
            FilledTonalButton(
                onClick = onRequestPermission,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("Enable", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Empty state card for location history section
 */
@Composable
private fun EmptyLocationHistoryCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "No location history yet",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "Locations will appear here when you create notes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Formats coordinates to display with 6 decimal places
 */
private fun formatCoordinates(latitude: Double, longitude: Double): String {
    return "${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"
}