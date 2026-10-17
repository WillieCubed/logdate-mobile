@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.provenance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.ui.glyphs.LogDateGlyph
import app.logdate.ui.glyphs.vector
import app.logdate.ui.theme.Spacing
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.captured_on_watch
import logdate.client.ui.generated.resources.captured_time_with_activity
import org.jetbrains.compose.resources.stringResource

/**
 * Whether moments show that they were captured on the watch.
 *
 * Off by default. The navigation root turns it on with the redesigned watch experience in Labs, so
 * leaf composables and screenshot scenes never read feature flags themselves.
 */
val LocalWatchProvenanceVisible: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** Whether to show that something was captured on the watch: it was, and provenance is on. */
@Composable
fun showsWatchProvenance(capturedOnWatch: Boolean): Boolean = capturedOnWatch && LocalWatchProvenanceVisible.current

/** The small watch glyph that marks a moment captured on the watch. */
@Composable
fun CapturedOnWatchGlyph(modifier: Modifier = Modifier) {
    Icon(
        imageVector = LogDateGlyph.WATCH.vector(),
        contentDescription = stringResource(Res.string.captured_on_watch),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(14.dp),
    )
}

/**
 * A moment's time as the line above its card shows it, then what the person was doing ("While moving")
 * and the watch glyph when the moment was captured on the watch. Without either it is exactly the line
 * the cards showed before.
 */
@Composable
fun CaptureTimeLabel(
    text: String,
    capturedOnWatch: Boolean,
    modifier: Modifier = Modifier,
    capturedDuring: CaptureActivity? = null,
) {
    val label =
        captureActivityText(capturedDuring)?.let { activity -> stringResource(Res.string.captured_time_with_activity, text, activity) }
            ?: text
    if (!showsWatchProvenance(capturedOnWatch)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(bottom = 4.dp),
        )
        return
    }
    Row(
        modifier = modifier.padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CapturedOnWatchGlyph()
    }
}

/** The line at the top of the editor when the entry being edited was captured on the watch. */
@Composable
fun CapturedOnWatchBanner(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.padding(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = LogDateGlyph.WATCH.vector(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(Res.string.captured_on_watch),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
