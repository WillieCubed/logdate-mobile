package app.logdate.feature.journals.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.shared.model.Journal
import app.logdate.ui.content.JournalContentCover
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.Clock
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A composable that displays a list of journals with their covers and titles.
 * 
 * @param journals The list of journals to display
 * @param onJournalClick Callback when a journal is clicked
 * @param modifier Modifier for the list
 * @param listState Optional state of the lazy list, allowing the parent to control scrolling.
 *                 If not provided, a new LazyListState will be created.
 */
@Composable
fun JournalsList(
    journals: List<Journal>,
    onJournalClick: (Journal) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        items(
            items = journals,
            key = { it.id.toString() }
        ) { journal ->
            JournalListItem(
                journal = journal,
                onClick = { onJournalClick(journal) }
            )
        }
    }
}

/**
 * A single item in the journals list.
 * 
 * @param journal The journal to display
 * @param onClick Callback when this journal is clicked
 * @param modifier Modifier for the item
 */
@Composable
private fun JournalListItem(
    journal: Journal,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Journal cover on the left
        JournalContentCover(
            imageUri = null // TODO: Add support for journal cover images
        )
        
        // Journal title on the right
        Text(
            text = journal.title.ifEmpty { "Untitled Journal" },
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Preview
@Composable
private fun JournalsListPreview() {
    val sampleJournals = listOf(
        Journal(
            title = "Travel Journal",
            created = Clock.System.now()
        ),
        Journal(
            title = "Daily Notes",
            created = Clock.System.now()
        ),
        Journal(
            title = "Work Log",
            created = Clock.System.now()
        ),
        Journal(
            title = "", // Will show as "Untitled Journal"
            created = Clock.System.now()
        )
    )
    
    JournalsList(
        journals = sampleJournals,
        onJournalClick = {}
    )
}