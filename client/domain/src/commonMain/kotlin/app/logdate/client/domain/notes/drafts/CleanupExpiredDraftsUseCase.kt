package app.logdate.client.domain.notes.drafts

import app.logdate.client.repository.journals.EntryDraftRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Use case for cleaning up expired entry drafts.
 *
 * Drafts older than [DEFAULT_MAX_AGE] are deleted automatically
 * when this use case is invoked.
 */
class CleanupExpiredDraftsUseCase(
    private val entryDraftRepository: EntryDraftRepository,
) {
    /**
     * Deletes drafts that have not been updated within [maxAge].
     *
     * @return The number of drafts deleted.
     */
    suspend operator fun invoke(maxAge: Duration = DEFAULT_MAX_AGE): Int = entryDraftRepository.deleteExpiredDrafts(maxAge)

    companion object {
        val DEFAULT_MAX_AGE: Duration = 30.days
    }
}
