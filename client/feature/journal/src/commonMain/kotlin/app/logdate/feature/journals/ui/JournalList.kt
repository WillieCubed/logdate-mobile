@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.journals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.shared.model.Journal
import app.logdate.ui.common.centeredGridPadding
import app.logdate.ui.theme.Spacing
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Grid layout for journals using adaptive columns (min 172dp each).
 *
 * The [gridState] is hoisted so the parent can observe scroll position — used by the
 * [JournalFilterBar] to change its background when content scrolls underneath.
 */
@Composable
fun JournalList(
    journals: List<JournalListItemUiState>,
    onOpenJournal: JournalClickCallback,
    onCreateJournal: () -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    LazyVerticalGrid(
        modifier = modifier,
        state = gridState,
        columns = GridCells.Adaptive(minSize = 172.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        contentPadding = centeredGridPadding(),
    ) {
        items(journals) { item ->
            when (item) {
                is JournalListItemUiState.ExistingJournal -> {
                    JournalCover(item.data, onClick = onOpenJournal)
                }

                is JournalListItemUiState.CreateJournalPlaceholder -> {
                    CreateJournalPlaceholder(
                        onClick = onCreateJournal,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun JournalListPreview() {
    val journals =
        List(10) {
            Journal(
                id = Uuid.random(),
                title = "Journal $it",
                created = Clock.System.now(),
                description = "Journal $it description",
                isFavorited = false,
                lastUpdated = Clock.System.now(),
            )
        }.map { JournalListItemUiState.ExistingJournal(it) }
    JournalList(journals, onOpenJournal = {}, onCreateJournal = {})
}
