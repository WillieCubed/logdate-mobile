package app.logdate.feature.editor.ui.editor.delegate

import app.logdate.client.domain.journals.GetDefaultSelectedJournalsUseCase
import app.logdate.feature.editor.ui.editor.EditorState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Delegate for handling journal selection in the editor.
 * This separates the journal selection logic from the ViewModel.
 */
class JournalSelectionDelegate(
    private val scope: CoroutineScope,
    private val getDefaultSelectedJournals: GetDefaultSelectedJournalsUseCase,
) {
    /**
     * Loads the default selected journals.
     *
     * @param currentState The current editor state flow
     */
    fun loadDefaultJournals(currentState: MutableStateFlow<EditorState>) {
        scope.launch {
            try {
                val defaultJournals = getDefaultSelectedJournals()
                // Only set the default journals if no selection has been made yet
                currentState.updateIfEmpty(defaultJournals)
            } catch (e: Exception) {
                Napier.e("Failed to load default journals: ${e.message}", e)
                // Don't update state on error
            }
        }
    }
    
    /**
     * Sets the journals that this entry is associated with.
     *
     * @param journalIds The journal IDs to select
     * @param currentState The current editor state flow
     */
    fun setSelectedJournals(
        journalIds: List<Uuid>,
        currentState: MutableStateFlow<EditorState>
    ) {
        currentState.update { it.copy(selectedJournalIds = journalIds) }
    }
    
    /**
     * Updates the state with default journals if the selection is empty.
     */
    private fun MutableStateFlow<EditorState>.updateIfEmpty(defaultJournals: List<Uuid>) {
        this.value = this.value.let { currentState ->
            if (currentState.selectedJournalIds.isEmpty()) {
                currentState.copy(selectedJournalIds = defaultJournals)
            } else {
                currentState
            }
        }
    }
    
    /**
     * Updates the state with the given transformer.
     */
    private fun MutableStateFlow<EditorState>.update(transformer: (EditorState) -> EditorState) {
        this.value = transformer(this.value)
    }
}