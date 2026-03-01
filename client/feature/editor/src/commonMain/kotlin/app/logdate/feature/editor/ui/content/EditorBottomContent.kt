package app.logdate.feature.editor.ui.content

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.feature.editor.ui.common.JournalSelectorDropdown
import app.logdate.shared.model.Journal
import kotlin.uuid.Uuid

@Composable
fun EditorBottomContent(
    availableJournals: List<Journal>,
    selectedJournalIds: List<Uuid>,
    onJournalSelectionChanged: (List<Uuid>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        JournalSelectorDropdown(
            availableJournals = availableJournals,
            selectedJournalIds = selectedJournalIds,
            onSelectionChanged = onJournalSelectionChanged
        )
    }
}
