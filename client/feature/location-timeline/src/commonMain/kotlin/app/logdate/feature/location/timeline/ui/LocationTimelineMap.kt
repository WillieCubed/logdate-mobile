@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.feature.location.timeline.ui.model.CurrentLocationUiModel
import app.logdate.feature.location.timeline.ui.model.LocationPlaceUiModel

@Composable
internal expect fun LocationTimelineMap(
    places: List<LocationPlaceUiModel>,
    currentLocation: CurrentLocationUiModel?,
    selectedPlaceId: String?,
    onSelectPlace: (String) -> Unit,
    modifier: Modifier = Modifier,
)
