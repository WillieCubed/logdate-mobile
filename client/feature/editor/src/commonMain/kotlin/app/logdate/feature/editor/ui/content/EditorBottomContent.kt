package app.logdate.feature.editor.ui.content

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.feature.editor.ui.common.JournalSelectorDropdown
import app.logdate.feature.editor.ui.state.EditorJournalState

/**
 * Bottom content for the editor, focused on journal selection.
 *
 * @param journalState Specialized state for journal selection 
 * @param modifier Optional modifier for customization
 */
@Composable
fun EditorBottomContent(
    journalState: EditorJournalState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Journal selector
        JournalSelectorDropdown(
            availableJournals = journalState.availableJournals,
            selectedJournalIds = journalState.selectedJournalIds,
            onSelectionChanged = journalState.onJournalSelectionChanged
        )
    }
}