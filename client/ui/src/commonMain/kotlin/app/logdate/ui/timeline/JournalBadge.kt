@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import kotlin.uuid.Uuid

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalBadgeRow(
    journals: List<JournalBadgeUiState>,
    onJournalClick: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (journals.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier,
    ) {
        journals.forEach { journal ->
            SuggestionChip(
                onClick = { onJournalClick(journal.journalId) },
                label = {
                    Text(
                        journal.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
    }
}
