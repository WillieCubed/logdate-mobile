package app.logdate.feature.editor.ui.editor.delegate

import app.logdate.client.domain.journals.GetDefaultSelectedJournalsUseCase
import app.logdate.client.domain.notes.FetchEntryUseCase
import app.logdate.client.repository.journals.EntryEditBase
import app.logdate.client.repository.journals.isCapturedOnWatch
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.mapper.toDomainBlock
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/** An entry loaded for editing, with whether the editor should say it was captured on the watch. */
data class LoadedEntry(
    val block: EntryBlockUiState,
    val capturedOnWatch: Boolean,
    val editBase: EntryEditBase? = null,
)

/**
 * Handles read-only content loading for the editor: existing entries and default journals.
 */
class ContentLoader(
    private val fetchEntryUseCase: FetchEntryUseCase,
    private val getDefaultSelectedJournals: GetDefaultSelectedJournalsUseCase,
) {
    /**
     * Loads an existing entry by ID and converts it to a UI block.
     */
    suspend fun loadEntry(entryId: Uuid): Result<LoadedEntry> =
        try {
            val edit = fetchEntryUseCase.forEditing(entryId)
            val entry = edit?.original
            if (entry != null) {
                Result.success(LoadedEntry(block = entry.toDomainBlock(), capturedOnWatch = entry.isCapturedOnWatch, editBase = edit))
            } else {
                Result.failure(NoSuchElementException("Entry not found: $entryId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Loads the default selected journals.
     *
     * @return The default journal IDs, or an empty list on failure.
     */
    suspend fun loadDefaultJournals(): List<Uuid> =
        try {
            getDefaultSelectedJournals()
        } catch (e: Exception) {
            Napier.e("Failed to load default journals: ${e.message}", e)
            emptyList()
        }
}
