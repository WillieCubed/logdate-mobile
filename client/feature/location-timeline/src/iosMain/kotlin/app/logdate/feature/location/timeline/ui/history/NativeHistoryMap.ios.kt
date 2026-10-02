@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.shared.model.location.LocationDayItem
import app.logdate.ui.workspace.PanelHeader

@Composable
internal actual fun NativeHistoryMap(
    items: List<LocationDayItem>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier,
    onInteraction: () -> Unit,
    historyKey: String?,
) {
    PanelHeader("Map unavailable on this device", modifier, "Your visits and memories are still available.")
}
