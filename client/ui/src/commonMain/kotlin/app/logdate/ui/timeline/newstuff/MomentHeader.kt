@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.timeline.newstuff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import app.logdate.client.awareness.daylight.DaylightPeriod
import app.logdate.client.awareness.daylight.stringRes
import app.logdate.ui.provenance.CapturedOnWatchGlyph
import app.logdate.ui.provenance.showsWatchProvenance
import app.logdate.ui.theme.Spacing
import app.logdate.ui.timeline.MomentUiState
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.timeline_moment_at_place
import org.jetbrains.compose.resources.stringResource

/**
 * A moment's label, followed by the watch glyph when everything in the moment was captured on the
 * watch. `MomentLabel` draws nothing for a blank label, so a moment with neither shows nothing.
 * A moment named only by its place reads "At <place>".
 */
@Composable
internal fun MomentHeader(
    moment: MomentUiState,
    timeOfDay: DaylightPeriod?,
    color: Color,
) {
    val placeLabel = moment.labelPlaceName?.let { place -> stringResource(Res.string.timeline_moment_at_place, place) }
    val label = moment.label.ifBlank { placeLabel.orEmpty() }
    if (!showsWatchProvenance(moment.capturedOnWatch)) {
        MomentLabel(label = label, timeOfDay = timeOfDay, color = color)
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        MomentLabel(label = label, timeOfDay = timeOfDay, color = color)
        CapturedOnWatchGlyph()
    }
}

@Composable
private fun MomentLabel(
    label: String,
    timeOfDay: DaylightPeriod?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (label.isBlank()) return
    val periodName = timeOfDay?.let { stringResource(it.stringRes) }
    val displayText =
        if (periodName != null && !label.lowercase().contains(periodName.lowercase())) {
            "$label \u00b7 $periodName"
        } else {
            label
        }
    Text(
        text = displayText,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}
