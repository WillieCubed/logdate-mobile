@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.feature.location.timeline.ui.model.CurrentLocationUiModel
import app.logdate.feature.location.timeline.ui.model.LocationPlaceUiModel

@Composable
internal actual fun LocationTimelineMap(
    places: List<LocationPlaceUiModel>,
    currentLocation: CurrentLocationUiModel?,
    selectedPlaceId: String?,
    onSelectPlace: (String) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Interactive map is available on Android.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
