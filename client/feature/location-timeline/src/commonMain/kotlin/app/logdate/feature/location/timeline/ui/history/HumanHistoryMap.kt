@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.shared.model.location.LocationDayItem

@Composable
internal expect fun HumanHistoryMap(
    items: List<LocationDayItem>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier,
    onInteraction: () -> Unit,
    viewportKey: String,
)
