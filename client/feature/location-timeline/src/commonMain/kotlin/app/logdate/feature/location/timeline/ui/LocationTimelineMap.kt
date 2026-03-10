@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.feature.location.timeline.ui.model.CurrentLocationUiModel
import app.logdate.feature.location.timeline.ui.model.LocationStopUiModel

@Composable
internal expect fun LocationTimelineMap(
    stops: List<LocationStopUiModel>,
    currentLocation: CurrentLocationUiModel?,
    selectedStopId: String?,
    onSelectStop: (String) -> Unit,
    modifier: Modifier = Modifier,
)
