package app.logdate.feature.journals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.logdate.shared.model.Journal
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateShort
import kotlinx.datetime.Clock
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A journal cover that displays basic information about a journal.
 *
 * @param journal The journal to display.
 * @param onClick The callback to invoke when the journal is clicked.
 * @param modifier The modifier to apply to the journal cover.
 * @param enabled Whether interaction events are enabled for the journal cover.
 * @param backgroundColor The background color of the journal cover. This is shown when the journal's
 * image is loading or unavailable.
 */
@Composable
fun JournalCover(
    journal: Journal,
    onClick: JournalClickCallback,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
) {
    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .background(
                color = backgroundColor,
                shape = JournalShape,
            )
            .clickable(enabled) { onClick(journal.id) }
            .widthIn(max = 256.dp)
        // TODO: Replace background with image
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.Bottom),
            horizontalAlignment = Alignment.Start,
        ) { // Actual content
            Text(
                text = journal.title,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Last updated ${journal.lastUpdated.toReadableDateShort()}",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
            )
            // TODO: Include people label
        }
    }
}


@Preview
@Composable
fun JournalCoverPreview() {
    LogDateTheme {
        JournalCover(
            Journal(
                id = "journal-1",
                title = "Diary",
                description = "Description",
                created = Clock.System.now(),
                isFavorited = false,
                lastUpdated = Clock.System.now(),
            ),
            modifier = Modifier.width(180.dp),
            onClick = {},
        )
    }
}