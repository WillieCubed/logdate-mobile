@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.timeline.ui.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import logdate.client.feature.timeline.generated.resources.Res
import logdate.client.feature.timeline.generated.resources.timeline_details_empty_placeholder
import org.jetbrains.compose.resources.stringResource

/**
 * A placeholder that is displayed when no timeline item is selected.
 */
@Composable
fun TimelineDetailsEmptyPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
        modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.timeline_details_empty_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
private fun TimelineDayEmptyPlaceholderPanelPreview() {
    TimelineDetailsEmptyPlaceholder(
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.large)
                .fillMaxSize(),
    )
}
