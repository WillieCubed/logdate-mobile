package app.logdate.feature.editor.ui.state

import androidx.compose.runtime.Immutable
import app.logdate.shared.model.Journal
import kotlin.uuid.Uuid

/**
 * Represents the state and callbacks related to journal selection in the editor.
 * Acts as a focused bus between the ViewModel and journal selection components.
 *
 * @property availableJournals List of all available journals
 * @property selectedJournalIds IDs of currently selected journals
 * @property onJournalSelectionChanged Callback for when the journal selection changes
 */
@Immutable
data class EditorJournalState(
    val availableJournals: List<Journal>,
    val selectedJournalIds: List<Uuid>,
    val onJournalSelectionChanged: (List<Uuid>) -> Unit,
)