package app.logdate.feature.journals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.model.Journal
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.Clock


@Composable
fun JournalList(
    journals: List<Journal>,
    onOpenJournal: JournalClickCallback,
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
            JournalCover(it, onOpenJournal)
        }
        item {
            Spacer(modifier = Modifier.height(Spacing.lg + 40.dp + Spacing.lg))
        }
    }
}

@Preview
@Composable
private fun JournalListPreview() {
    val journals = List(10) {
        Journal(
            id = it.toString(),
            title = "Journal $it",
            created = Clock.System.now(),
            description = "Journal $it description",
            isFavorited = false,
            lastUpdated = Clock.System.now(),
        )
    }
    JournalList(journals, onOpenJournal = {})
}