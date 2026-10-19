package app.logdate.client.domain.notes

import app.logdate.client.repository.journals.EntryEditBase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
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
    suspend fun forEditing(entryId: Uuid): EntryEditBase? {
        val scope = journalNotesRepository.getNoteRecoveryScope()
        val note = invoke(entryId) ?: return null
        check(scope == journalNotesRepository.getNoteRecoveryScope()) { "The entry account changed while loading" }
        return EntryEditBase(note, scope)
    }

    suspend operator fun invoke(entryId: Uuid): JournalNote? =
        try {
            val entry = journalNotesRepository.getNoteById(entryId)
            if (entry != null) {
                Napier.d("FetchEntryUseCase: Found entry $entryId")
            } else {
                Napier.w("FetchEntryUseCase: Entry not found: $entryId")
            }
            entry
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Napier.e("FetchEntryUseCase: Failed to fetch entry: $entryId", e)
            null
        }
}
