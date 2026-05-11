@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.timeline.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun TimelineLocationsMap(
    locations: List<DayLocation>,
    modifier: Modifier,
) {
    TimelineLocationsStaticPreview(
        locations = locations,
        modifier = modifier,
    )
}
