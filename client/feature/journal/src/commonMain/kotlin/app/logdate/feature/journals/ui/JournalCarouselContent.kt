@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.journals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.action_browse_journals
import org.jetbrains.compose.resources.stringResource

/**
 * Carousel layout for the journals overview: a horizontal cover-flow carousel
 * centered vertically with a "See all journals" button below.
 */
@Composable
fun JournalCarouselContent(
    journals: List<JournalListItemUiState>,
    onOpenJournal: JournalClickCallback,
    onCreateJournal: () -> Unit,
    onBrowseJournals: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val reservedForButton =
            (maxHeight * 0.12f).coerceIn(44.dp, 72.dp)
        val contentSpacing =
            (maxHeight * 0.03f).coerceIn(Spacing.sm, Spacing.lg)
        val carouselMaxHeight =
            (maxHeight - reservedForButton).coerceAtLeast(220.dp)

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement =
                Arrangement.spacedBy(
                    contentSpacing,
                    Alignment.CenterVertically,
                ),
        ) {
            JournalCoverFlowCarousel(
                journals = journals,
                onOpenJournal = onOpenJournal,
                onCreateJournal = onCreateJournal,
                maxCardHeight = carouselMaxHeight,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = carouselMaxHeight),
            )
            TextButton(
                onClick = onBrowseJournals,
                modifier =
                    Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text =
                        stringResource(
                            Res.string.action_browse_journals,
                        ),
                )
            }
        }
    }
}
