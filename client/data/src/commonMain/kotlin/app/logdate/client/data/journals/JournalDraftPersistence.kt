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
    ): Boolean {
        requireStorageScope(recoveryScope)
        val changed = drafts.compareAndSetDraftForRestore(id, expected, replacement, recoveryScope)
        requireStorageScope(recoveryScope)
        if (changed) {
            val operation = if (replacement == null) PendingOperation.DELETE else PendingOperation.UPDATE
            val captured = uploadScope(recoveryScope)
            if (captured == null) {
                enqueue(id, operation)
            } else {
                metadata.enqueuePendingForScope(id.toString(), EntityType.DRAFT, operation, captured)
                this.changed()
            }
        }
        return changed
    }

    private suspend fun enqueue(
        id: Uuid,
        operation: PendingOperation,
    ) {
        metadata.enqueuePending(id.toString(), EntityType.DRAFT, operation)
        changed()
    }
}
