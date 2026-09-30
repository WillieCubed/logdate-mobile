@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import app.logdate.shared.model.location.JourneyLeg
import app.logdate.shared.model.location.LocationDayItem
import app.logdate.shared.model.location.PlaceVisit
import app.logdate.ui.maps.rememberGoogleMapsEnabled
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
internal actual fun HumanHistoryMap(
    items: List<LocationDayItem>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier,
    onInteraction: () -> Unit,
    viewportKey: String,
) {
    if (!rememberGoogleMapsEnabled()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Your visits are available below while the map is unavailable.") }
        return
    }
    val camera = rememberCameraPositionState()
    var loaded by remember { mutableStateOf(false) }
    val interact by rememberUpdatedState(onInteraction)
    val points =
        items.flatMap { item ->
            when (item) {
                is PlaceVisit -> listOf(LatLng(item.latitude, item.longitude))
                is JourneyLeg -> item.route.map { LatLng(it.latitude, it.longitude) }
                else -> emptyList()
            }
        }
    val bounds = remember(points) { if (points.size > 1) LatLngBounds.builder().apply { points.forEach { include(it) } }.build() else null }
    LaunchedEffect(loaded, viewportKey, points.isEmpty()) {
        if (!loaded) return@LaunchedEffect
        val selected = items.firstOrNull { it.id == selectedId }
        val target =
            when (selected) {
                is PlaceVisit -> LatLng(selected.latitude, selected.longitude)
                is JourneyLeg -> selected.route.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
                else -> null
            }
        when {
            target != null -> camera.move(CameraUpdateFactory.newLatLngZoom(target, 15f))
            bounds != null -> camera.move(CameraUpdateFactory.newLatLngBounds(bounds, 64))
            points.isNotEmpty() -> camera.move(CameraUpdateFactory.newLatLngZoom(points.first(), 14f))
        }
    }
    GoogleMap(
        modifier =
            modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }) interact()
                    }
                }
            },
        cameraPositionState = camera,
        onMapLoaded = { loaded = true },
    ) {
        items.forEach { item ->
            when (item) {
                is PlaceVisit ->
                    Marker(
                        state = remember(item.id, item.latitude, item.longitude) { MarkerState(LatLng(item.latitude, item.longitude)) },
                        title = item.place?.name ?: "A place you visited",
                        onClick = {
                            onSelect(item.id)
                            true
                        },
                    )
                is JourneyLeg ->
                    if (item.route.size > 1) {
                        Polyline(
                            points = item.route.map { LatLng(it.latitude, it.longitude) },
                            clickable = true,
                            onClick = { onSelect(item.id) },
                        )
                    }
                else -> Unit
            }
        }
    }
}
