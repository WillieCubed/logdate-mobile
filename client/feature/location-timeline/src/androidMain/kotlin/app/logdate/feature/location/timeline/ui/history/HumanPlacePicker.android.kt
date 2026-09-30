@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.shared.model.location.SemanticPlace
import app.logdate.ui.maps.rememberGoogleMapsEnabled
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
internal actual fun HumanPlacePicker(
    selected: SemanticPlace?,
    onPoint: (Double, Double) -> Unit,
    modifier: Modifier,
) {
    if (!rememberGoogleMapsEnabled()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("The map is unavailable. Choose a saved place, or try again later.") }
        return
    }
    val camera =
        rememberCameraPositionState {
            position =
                CameraPosition.fromLatLngZoom(
                    selected?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(0.0, 0.0),
                    if (selected ==
                        null
                    ) {
                        1f
                    } else {
                        15f
                    },
                )
        }
    GoogleMap(modifier, cameraPositionState = camera, onMapClick = { onPoint(it.latitude, it.longitude) }) {
        selected?.let { place ->
            Marker(
                state = remember(place.latitude, place.longitude) { MarkerState(LatLng(place.latitude, place.longitude)) },
                title = place.name,
            )
        }
    }
}
