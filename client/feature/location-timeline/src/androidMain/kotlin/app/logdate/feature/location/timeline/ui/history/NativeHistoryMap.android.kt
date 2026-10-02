@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.logdate.shared.model.location.LocationDayItem
import app.logdate.ui.maps.rememberGoogleMapsEnabled
import app.logdate.ui.platform.rememberSystemReduceMotion
import app.logdate.ui.workspace.LocalSupportingOverlap
import app.logdate.ui.workspace.PanelHeader
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay

@Composable
internal actual fun NativeHistoryMap(
    items: List<LocationDayItem>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier,
    onInteraction: () -> Unit,
    historyKey: String?,
) {
    val geometry = remember(items) { historyMapGeometry(items) }
    val enabled = rememberGoogleMapsEnabled()
    if (!enabled) {
        PanelHeader("Map unavailable", modifier, "Your visits and memories are still available below.")
        return
    }
    val first = geometry.coordinates.firstOrNull()?.toLatLng() ?: LatLng(36.17, -115.14)
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(first, 12f) }
    val overlap = LocalSupportingOverlap.current
    val reduceMotion by rememberSystemReduceMotion()
    val dayKey = historyKey ?: items.firstOrNull()?.id
    var fitted by rememberSaveable(dayKey) { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var timedOut by remember { mutableStateOf(false) }
    var previousSelection by remember { mutableStateOf(selectedId) }
    LaunchedEffect(loaded) {
        if (!loaded) {
            delay(15_000)
            timedOut = true
        }
    }
    LaunchedEffect(loaded, dayKey, geometry.coordinates.isEmpty()) {
        if (loaded && shouldFitHistoryDay(fitted, geometry.coordinates.isNotEmpty())) {
            val points = geometry.coordinates.map { it.toLatLng() }
            if (points.distinct().size == 1) {
                camera.move(CameraUpdateFactory.newLatLngZoom(points.first(), 15f))
            } else {
                camera.move(CameraUpdateFactory.newLatLngBounds(LatLngBounds.builder().apply { points.forEach(::include) }.build(), 48))
            }
            fitted = true
        }
    }
    LaunchedEffect(selectedId, loaded) {
        if (shouldMoveHistoryCamera(loaded, previousSelection, selectedId)) {
            val target =
                geometry.markers.firstOrNull { it.id == selectedId }?.coordinate
                    ?: geometry.paths
                        .firstOrNull { it.id == selectedId }
                        ?.points
                        ?.firstOrNull()
            target?.let {
                val update = CameraUpdateFactory.newLatLngZoom(it.toLatLng(), 15f)
                if (reduceMotion) camera.move(update) else camera.animate(update, 350)
            }
        }
        previousSelection = advanceHistorySelection(loaded, previousSelection, selectedId)
    }
    LaunchedEffect(camera) {
        snapshotFlow { camera.isMoving && camera.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE }
            .collect { if (it) onInteraction() }
    }
    Box(modifier) {
        GoogleMap(
            modifier =
                Modifier.fillMaxSize().testTag("history-native-map").semantics {
                    stateDescription =
                        if (loaded) "Map loaded" else "Map loading"
                },
            cameraPositionState = camera,
            contentPadding = PaddingValues(bottom = overlap + 8.dp),
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings =
                MapUiSettings(
                    compassEnabled = false,
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false,
                ),
            onMapLoaded = {
                loaded = true
                timedOut = false
            },
        ) {
            geometry.markers.forEach { marker ->
                Marker(
                    state = remember(marker.id, marker.coordinate) { MarkerState(marker.coordinate.toLatLng()) },
                    title = marker.label,
                    icon =
                        BitmapDescriptorFactory.defaultMarker(
                            if (marker.id ==
                                selectedId
                            ) {
                                BitmapDescriptorFactory.HUE_AZURE
                            } else {
                                BitmapDescriptorFactory.HUE_RED
                            },
                        ),
                    onClick = {
                        onInteraction()
                        onSelect(marker.id)
                        true
                    },
                )
            }
            geometry.paths.forEach { path ->
                Polyline(
                    points = path.points.map { it.toLatLng() },
                    clickable = true,
                    width = if (path.id == selectedId) 8f else 4f,
                    color = if (path.id == selectedId) Color(0xFF3569B4) else Color(0xFF687381),
                    onClick = {
                        onInteraction()
                        onSelect(path.id)
                    },
                )
            }
        }
        if (timedOut) PanelHeader("Map is taking longer to load", subtitle = "You can still browse your history below.")
    }
}

private fun HistoryCoordinate.toLatLng() = LatLng(latitude, longitude)
