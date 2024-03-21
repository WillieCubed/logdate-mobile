package app.logdate.feature.journals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.core.data.TEST_JOURNALS
import app.logdate.model.Journal
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateShort
import kotlinx.datetime.Clock

@Composable
fun JournalsRoute(
    onOpenJournal: JournalOpenCallback,
    modifier: Modifier = Modifier,
    viewModel: JournalsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    JournalsScreen(state, onOpenJournal, modifier)
}

@Composable
internal fun JournalsScreen(
    state: JournalsUiState,
    onOpenJournal: JournalOpenCallback,
    modifier: Modifier = Modifier
) {
    when (state) {
        JournalsUiState.Loading -> JournalListPlaceholder()
        is JournalsUiState.Success -> JournalList(state.journals, onOpenJournal, modifier)
    }
}

@Composable
fun JournalListPlaceholder(modifier: Modifier = Modifier, journals: Int = 4) {
    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Adaptive(minSize = 172.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        items(journals) {
            Box(
                modifier = Modifier
                    .aspectRatio(9f / 16f)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = JournalShape,
                    )
            )
        }
    }
}

@Preview
@Composable
fun JournalsScreenPreview() {
    LogDateTheme {
        JournalsScreen(state = JournalsUiState.Success(TEST_JOURNALS), onOpenJournal = {})
    }
}

@Composable
fun JournalList(
    journals: List<Journal>,
    onOpenJournal: JournalOpenCallback,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Adaptive(minSize = 172.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        contentPadding = PaddingValues(Spacing.lg)
    ) {
        items(journals) {
            JournalListItem(it, onOpenJournal)
        }
        item {
            Spacer(modifier = Modifier.height(Spacing.lg + 40.dp + Spacing.lg))
        }
    }
}

@Composable
fun JournalListItem(
    journal: Journal,
    onOpenJournal: JournalOpenCallback,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .clickable { onOpenJournal(journal.id) }
            .background(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = JournalShape,
            ) // TODO: Replace background with image
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
fun JournalListItemPreview() {
    LogDateTheme {
        JournalListItem(
            Journal(
                id = "journal-1",
                title = "Diary",
                description = "Description",
                created = Clock.System.now(),
                isFavorited = false,
                lastUpdated = Clock.System.now(),
            ),
            modifier = Modifier.width(180.dp),
            onOpenJournal = {},
        )
    }
}

/**
 * A [CornerBasedShape] that represents the shape of a journal item.
 */
internal val JournalShape = RoundedCornerShape(
    topEnd = 16.dp,
    bottomEnd = 16.dp,
)

typealias JournalOpenCallback = (journalId: String) -> Unit
