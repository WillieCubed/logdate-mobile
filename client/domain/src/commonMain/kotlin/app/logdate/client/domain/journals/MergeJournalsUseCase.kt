package app.logdate.client.domain.journals

import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalMergeCandidate
import app.logdate.client.repository.journals.JournalMergePreview
import app.logdate.client.repository.journals.JournalMergeResult
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.SyncManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlin.uuid.Uuid

class MergeJournalsUseCase(
    private val journals: JournalRepository,
    private val memberships: JournalContentRepository,
    private val syncManager: SyncManager,
) {
    fun observeIssues() = journals.observeJournalMergeIssues()

    fun observeCandidates() =
        combine(journals.allJournalsObserved, memberships.observeJournalItemCounts()) { journals, counts ->
            journals
                .map { JournalMergeCandidate(it, counts[it.id] ?: 0) }
                .sortedByDescending { it.journal.lastUpdated }
        }

    suspend fun preview(
        sourceId: Uuid,
        destinationId: Uuid,
    ) = journals.previewMerge(sourceId, destinationId)

    suspend fun previewPending(
        operationId: Uuid,
        destinationId: Uuid,
    ) = journals.previewPendingMerge(operationId, destinationId)

    suspend fun operation(operationId: Uuid) = journals.getJournalMerge(operationId)

    suspend operator fun invoke(
        preview: JournalMergePreview,
        operationId: Uuid = Uuid.random(),
    ): JournalMergeResult = commit { journals.merge(preview, operationId) }

    suspend fun retarget(
        operationId: Uuid,
        preview: JournalMergePreview,
    ): JournalMergeResult = commit { journals.retargetPendingMerge(operationId, preview, Uuid.random()) }

    private suspend fun commit(persist: suspend () -> JournalMergeResult): JournalMergeResult {
        val result =
            try {
                syncManager.whilePaused { persist() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Napier.e("Failed to merge journals", error)
                JournalMergeResult.Failed
            }
        if (result is JournalMergeResult.Merged) {
            // A later upload failure cannot turn a committed local merge into a local failure.
            try {
                syncManager.sync(startNow = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Napier.w("Journal merge is waiting for sync", error)
            }
        }
        return result
    }
}
