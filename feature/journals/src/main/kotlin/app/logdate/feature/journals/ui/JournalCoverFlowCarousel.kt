package app.logdate.feature.journals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.model.Journal
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.Clock

/**
 * A carousel that displays journal covers in a flow layout.
 *
 * If there is only one journal, it is displayed in the center of the view.
 * If there are two journals, they are displayed side by side.
 * If there are three or more journals:
 * - One journal is displayed in the center of the view larger than the others
 * - The other journals are displayed on the left and right of the center journal
 * - Swiping between journals will decrease the size of the center journal and increase the size of
 *   the journal that is being swiped to while being swiped.
 */
@Composable
fun JournalCoverFlowCarousel(
    journals: List<Journal>,
    onOpenJournal: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (journals.isEmpty()) {
        return
    }

    val listState = rememberLazyListState()
    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    val displayList: List<Journal> = remember(journals) {
        when {
            journals.size < 3 -> journals
            else -> (journals.takeLast(1) + journals + journals.take(1))
        }
    }

    LaunchedEffect(firstVisibleItemIndex) {
        if (firstVisibleItemIndex == journals.size - 1) {
            listState.scrollToItem(1)
        } else if (firstVisibleItemIndex == 0) {
            listState.scrollToItem(journals.size - 2)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        contentPadding = PaddingValues(horizontal = Spacing.lg)
    ) {
        itemsIndexed(displayList) { index, journal ->
            val actualIndex = index % journals.size
            JournalCover(
                journal = journal,
                onClick = { onOpenJournal(journal.id) },
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier
                    .widthIn(min = 240.dp)
                    .heightIn(max = 400.dp),
            )
        }

    }
}

@Preview
@Composable
private fun JournalCoverFlowPreview() {
    JournalCoverFlowCarousel(
        journals = listOf(
            Journal(
                id = "1",
                title = "Journal 1",
                created = Clock.System.now(),
                description = "Journal 1 description",
                isFavorited = false,
                lastUpdated = Clock.System.now(),
            ),
            Journal(
                id = "2",
                title = "Journal 2",
                created = Clock.System.now(),
                description = "Journal 2 description",
                isFavorited = false,
                lastUpdated = Clock.System.now(),
            ),
            Journal(
                id = "3",
                title = "Journal 3",
                created = Clock.System.now(),
                description = "Journal 3 description",
                isFavorited = false,
                lastUpdated = Clock.System.now(),
            ),
        ),
        onOpenJournal = {},
    )
}