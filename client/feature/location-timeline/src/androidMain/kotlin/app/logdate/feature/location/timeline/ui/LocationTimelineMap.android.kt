package app.logdate.feature.location.timeline.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.feature.location.timeline.ui.model.CurrentLocationUiModel
import app.logdate.feature.location.timeline.ui.model.LocationStopUiModel
import app.logdate.ui.maps.rememberGoogleMapsEnabled
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import logdate.client.feature.location.timeline.generated.resources.Res
import logdate.client.feature.location.timeline.generated.resources.current_location
import logdate.client.feature.location.timeline.generated.resources.recent_stops
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun locationTimelineMap(
    stops: List<LocationStopUiModel>,
    currentLocation: CurrentLocationUiModel?,
    selectedStopId: String?,
    onSelectStop: (String) -> Unit,
    modifier: Modifier,
) {
    val selectedStop = stops.firstOrNull { it.id == selectedStopId } ?: stops.firstOrNull()
    val initialPoint = selectedStop?.let { LatLng(it.latitude, it.longitude) } ?: currentLocation?.let { LatLng(it.latitude, it.longitude) }
    val googleMapsEnabled = rememberGoogleMapsEnabled()

    if (initialPoint == null) {
        locationTimelineMapFallback(
            stops = stops,
            selectedStop = selectedStop,
            currentLocation = currentLocation,
            modifier = modifier,
        )
        return
    }

    if (!googleMapsEnabled) {
        locationTimelineMapFallback(
            stops = stops,
            selectedStop = selectedStop,
            currentLocation = currentLocation,
            modifier = modifier,
        )
        return
    }

    val cameraPositionState =
        rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(initialPoint, if (stops.size > 1) 11f else 14f)
        }

    LaunchedEffect(selectedStopId, stops, currentLocation) {
        val target =
            stops.firstOrNull { it.id == selectedStopId }?.let { LatLng(it.latitude, it.longitude) }
                ?: currentLocation?.let { LatLng(it.latitude, it.longitude) }
                ?: stops.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }

        if (target != null) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(target, if (selectedStopId != null) 14f else 11f),
                durationMs = 600,
            )
        }
    }

    GoogleMap(
        modifier =
            modifier
                .clip(RoundedCornerShape(24.dp)),
        cameraPositionState = cameraPositionState,
        properties = remember { MapProperties(isMyLocationEnabled = false) },
        uiSettings =
            remember {
                MapUiSettings(
                    compassEnabled = false,
                    mapToolbarEnabled = false,
                    myLocationButtonEnabled = false,
                    zoomControlsEnabled = false,
                )
            },
    ) {
        currentLocation?.let { location ->
            Marker(
                state = MarkerState(position = LatLng(location.latitude, location.longitude)),
                title = location.title,
                snippet = location.subtitle,
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
            )
        }

        stops.forEach { stop ->
            Marker(
                state = MarkerState(position = LatLng(stop.latitude, stop.longitude)),
                title = stop.title,
                snippet = "${stop.timeRange}\nStayed ${stop.duration}",
                onClick = {
                    onSelectStop(stop.id)
                    false
                },
            )
        }
    }
}

@Composable
private fun locationTimelineMapFallback(
    stops: List<LocationStopUiModel>,
    selectedStop: LocationStopUiModel?,
    currentLocation: CurrentLocationUiModel?,
    modifier: Modifier = Modifier,
) {
    val primaryStop = selectedStop ?: stops.firstOrNull()
    val supportingStops = stops.filterNot { it.id == primaryStop?.id }.take(2)
    val primaryTitle = primaryStop?.title ?: currentLocation?.title ?: stringResource(Res.string.current_location)
    val primarySubtitle = primaryStop?.timeRange ?: currentLocation?.subtitle ?: primaryStop?.subtitle.orEmpty()

    Surface(
        modifier = modifier.clip(RoundedCornerShape(24.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush =
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                    ),
                            ),
                    ),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 18.dp, end = 18.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    ) {
                        Text(
                            text = stringResource(Res.string.recent_stops),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = primaryTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (primarySubtitle.isNotBlank()) {
                        Text(
                            text = primarySubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    primaryStop?.let { stop ->
                        stopSummaryPill(
                            title = stop.title,
                            subtitle = stop.duration,
                        )
                    } ?: currentLocation?.let { location ->
                        stopSummaryPill(
                            title = location.title,
                            subtitle = location.subtitle,
                        )
                    }

                    supportingStops.forEach { stop ->
                        stopSummaryPill(
                            title = stop.title,
                            subtitle = stop.duration,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun stopSummaryPill(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
