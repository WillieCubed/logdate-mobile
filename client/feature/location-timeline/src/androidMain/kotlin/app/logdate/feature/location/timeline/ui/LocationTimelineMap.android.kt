package app.logdate.feature.location.timeline.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.logdate.feature.location.timeline.ui.model.CurrentLocationUiModel
import app.logdate.feature.location.timeline.ui.model.LocationStopUiModel
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

    if (initialPoint == null) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Map unavailable",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
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
