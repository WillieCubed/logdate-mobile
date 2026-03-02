package app.logdate.client.domain.notes

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid

/**
 * Fetches a specific journal entry by its ID for editing in a new window.
 *
 * This use case retrieves an entry from the JournalNotesRepository, which abstracts access to the underlying
 * data layer. It supports loading entries for multi-window editing on large screens and desktop devices.
 *
 * @param entryId The unique identifier of the entry to fetch
 * @return The journal note if found, null if the entry doesn't exist or an error occurs
 */
class FetchEntryUseCase(
    private val journalNotesRepository: JournalNotesRepository,
) {
    suspend operator fun invoke(entryId: Uuid): JournalNote? =
        try {
            val entry = journalNotesRepository.getNoteById(entryId)
            if (entry != null) {
                Napier.d("FetchEntryUseCase: Found entry $entryId")
            } else {
                Napier.w("FetchEntryUseCase: Entry not found: $entryId")
            }
            entry
        } catch (e: Exception) {
            Napier.e("FetchEntryUseCase: Failed to fetch entry: $entryId", e)
            null
        }
}
