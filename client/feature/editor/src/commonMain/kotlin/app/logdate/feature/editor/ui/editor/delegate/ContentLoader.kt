package app.logdate.feature.editor.ui.editor.delegate

import app.logdate.client.domain.journals.GetDefaultSelectedJournalsUseCase
import app.logdate.client.domain.notes.FetchEntryUseCase
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.mapper.toDomainBlock
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

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
    suspend fun loadEntry(entryId: Uuid): Result<EntryBlockUiState> =
        try {
            val entry = fetchEntryUseCase(entryId)
            if (entry != null) {
                Result.success(entry.toDomainBlock())
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
