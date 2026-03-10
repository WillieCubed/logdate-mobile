@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.timeline.ui.details

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
internal actual fun TimelineLocationsMap(
    locations: List<DayLocation>,
    modifier: Modifier,
) {
    if (locations.isEmpty()) {
        return
    }

    val initialPoint = LatLng(locations.first().latitude, locations.first().longitude)
    val cameraPositionState =
        rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(initialPoint, if (locations.size == 1) 13f else 10f)
        }

    LaunchedEffect(locations) {
        if (locations.size == 1) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(initialPoint, 13f),
                durationMs = 600,
            )
            return@LaunchedEffect
        }

        val bounds =
            LatLngBounds
                .Builder()
                .apply {
                    locations.forEach { location ->
                        include(LatLng(location.latitude, location.longitude))
                    }
                }.build()

        runCatching {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngBounds(bounds, 120),
                durationMs = 600,
            )
        }.onFailure {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(initialPoint, 10f),
                durationMs = 600,
            )
        }
    }

    GoogleMap(
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
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
        locations.forEach { location ->
            Marker(
                state = MarkerState(position = LatLng(location.latitude, location.longitude)),
                title = location.name,
            )
        }
    }
}
