@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.timeline.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun TimelineLocationsMap(
    locations: List<DayLocation>,
    modifier: Modifier = Modifier,
)
