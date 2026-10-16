package app.logdate.client.data.journals

import app.logdate.client.repository.journals.DraftRecoveryScope
import app.logdate.client.repository.journals.DraftRepository
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.model.EditorDraft
import kotlin.uuid.Uuid

internal class JournalDraftPersistence(
    private val drafts: DraftRepository,
    private val metadata: SyncMetadataService,
    private val resolve: suspend (EditorDraft) -> EditorDraft,
    private val changed: () -> Unit,
    private val transactions: app.logdate.client.sync.SyncTransactionManager = DirectArchiveRecoveryTransactions,
) {
    suspend fun save(draft: EditorDraft) {
        drafts.saveDraft(resolve(draft))
        enqueue(draft.id, PendingOperation.UPDATE)
    }

    suspend fun delete(id: Uuid) {
        drafts.deleteDraft(id)
        enqueue(id, PendingOperation.DELETE)
    }

    suspend fun captureScope(currentOutboxScope: suspend () -> JournalMergeScope): DraftRecoveryScope? {
        val storage = drafts.getDraftRecoveryScope() ?: return null
        val outbox = currentOutboxScope()
        check(drafts.getDraftRecoveryScope() == storage) { "Draft recovery account changed" }
        check(storage.ownerId == null || storage.ownerId == outbox.ownerId && storage.serverOrigin == outbox.serverOrigin) {
            "Draft recovery storage and upload scope differ"
        }
        return storage.copy(outboxOwnerId = outbox.ownerId, outboxServerOrigin = outbox.serverOrigin)
    }

    private suspend fun requireStorageScope(scope: DraftRecoveryScope?) {
        val current = drafts.getDraftRecoveryScope()
        check(
            if (scope ==
                null
            ) {
                current == null
            } else {
                current != null && current.ownerId == scope.ownerId && current.serverOrigin == scope.serverOrigin
            },
        ) {
            "Draft recovery account changed"
        }
    }

    private fun uploadScope(scope: DraftRecoveryScope?): UploadScope? =
        scope?.outboxOwnerId?.let { owner -> scope.outboxServerOrigin?.let { UploadScope(owner, it) } }

    suspend fun getForRestore(id: Uuid): EditorDraft? = drafts.getDraftForRestore(id)

    suspend fun ensureRecoveryQueued(
        id: Uuid,
        recoveryScope: DraftRecoveryScope?,
    ) {
        requireStorageScope(recoveryScope)
        if (drafts.getDraftForRestore(id) == null) return
        requireStorageScope(recoveryScope)
        val captured = uploadScope(recoveryScope)
        if (captured == null) {
            metadata.enqueueRepairIfAbsent(id.toString(), EntityType.DRAFT)
        } else {
            metadata.enqueueRepairForScopeIfAbsent(id.toString(), EntityType.DRAFT, captured)
        }
        requireStorageScope(recoveryScope)
        changed()
    }

    suspend fun compareAndSet(
        id: Uuid,
        expected: EditorDraft?,
        replacement: EditorDraft?,
        recoveryScope: DraftRecoveryScope? = null,
        enqueue: Boolean = true,
        recoveryId: String? = null,
    ): Boolean {
        val applied =
            transactions.withTransaction {
                requireStorageScope(recoveryScope)
                if (recoveryId != null && drafts.hasArchiveRecoveryDecision(id, recoveryScope, recoveryId)) return@withTransaction false
                val current = drafts.getDraftForRestore(id)
                if (current != expected) {
                    drafts.compareAndSetDraftForRestore(id, current, current, recoveryScope, recoveryId)
                    return@withTransaction false
                }
                if (!enqueue || expected == replacement) {
                    return@withTransaction drafts.compareAndSetDraftForRestore(id, expected, replacement, recoveryScope, recoveryId)
                }
                val captured = uploadScope(recoveryScope)
                val operation = if (replacement == null) PendingOperation.DELETE else PendingOperation.UPDATE
                if (replacement != null) {
                    if (!metadata.enqueueRestoreMutation(id.toString(), EntityType.DRAFT, operation, captured)) {
                        drafts.compareAndSetDraftForRestore(id, current, current, recoveryScope, recoveryId)
                        requireStorageScope(recoveryScope)
                        return@withTransaction false
                    }
                } else if (captured == null) {
                    metadata.enqueuePending(id.toString(), EntityType.DRAFT, operation)
                } else {
                    metadata.enqueuePendingForScope(id.toString(), EntityType.DRAFT, operation, captured)
                }
                val stored = drafts.compareAndSetDraftForRestore(id, expected, replacement, recoveryScope, recoveryId)
                requireStorageScope(recoveryScope)
                stored
            }
        if (applied && enqueue && expected != replacement) changed()
        return applied
    }

    private suspend fun enqueue(
        id: Uuid,
        operation: PendingOperation,
    ) {
        metadata.enqueuePending(id.toString(), EntityType.DRAFT, operation)
        changed()
    }
}

internal suspend fun app.logdate.client.repository.journals.JournalRepository.resolveDraft(draft: EditorDraft): EditorDraft =
    draft.copy(selectedJournalIds = draft.selectedJournalIds.map { resolveJournalId(it) }.distinct())
