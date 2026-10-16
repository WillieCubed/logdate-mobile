package app.logdate.client.data.journals

import app.logdate.client.data.fakes.FakeDraftRepository
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.model.EditorDraft
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class JournalDraftRecoveryDeletionTest {
    @Test
    fun `a changed snapshot cannot admit a later matching value without an outbox decision`() =
        runTest {
            val base = FakeDraftRepository()
            val expected = EditorDraft()
            val changed = expected.copy(lastModifiedAt = kotlin.time.Instant.DISTANT_FUTURE)
            base.saveDraft(expected)
            var first = true
            val drafts =
                object : app.logdate.client.repository.journals.DraftRepository by base {
                    override suspend fun getDraftForRestore(id: kotlin.uuid.Uuid): EditorDraft? {
                        if (first) {
                            first = false
                            return changed
                        }
                        return base.getDraft(id)
                    }

                    override suspend fun compareAndSetDraftForRestore(
                        id: kotlin.uuid.Uuid,
                        expected: EditorDraft?,
                        replacement: EditorDraft?,
                        recoveryScope: app.logdate.client.repository.journals.DraftRecoveryScope?,
                        recoveryId: String?,
                    ): Boolean {
                        if (base.getDraft(id) != expected) return false
                        if (replacement != null) base.saveDraft(replacement)
                        return true
                    }
                }
            val metadata = FakeSyncMetadataService()
            val replacement = expected.copy(lastModifiedAt = kotlin.time.Instant.DISTANT_PAST)
            assertFalse(JournalDraftPersistence(drafts, metadata, { it }, {}).compareAndSet(expected.id, expected, replacement))
            assertEquals(expected, base.getDraft(expected.id))
            assertEquals(0, metadata.getPendingCount())
        }

    @Test
    fun `cancellation while a deletion wins cannot leave a recovered local draft`() =
        runTest {
            val draft = EditorDraft()
            val drafts = FakeDraftRepository()
            val metadata =
                object : FakeSyncMetadataService() {
                    override suspend fun enqueueRestoreMutation(
                        entityId: String,
                        entityType: EntityType,
                        operation: PendingOperation,
                        scope: UploadScope?,
                    ): Boolean {
                        enqueuePending(entityId, entityType, PendingOperation.DELETE)
                        throw kotlinx.coroutines.CancellationException("Interrupted after deletion won")
                    }
                }
            val persistence = JournalDraftPersistence(drafts, metadata, { it }, {})
            kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
                persistence.compareAndSet(draft.id, null, draft)
            }
            assertEquals(null, drafts.getDraft(draft.id))
        }

    @Test
    fun `metadata replay cannot recreate a draft with a queued deletion`() =
        runTest {
            val draft = EditorDraft()
            val drafts = FakeDraftRepository()
            val metadata = FakeSyncMetadataService()
            metadata.enqueuePending(draft.id.toString(), EntityType.DRAFT, PendingOperation.DELETE)
            val persistence = JournalDraftPersistence(drafts, metadata, { it }, {})
            assertFalse(persistence.compareAndSet(draft.id, null, draft))
            assertEquals(null, drafts.getDraft(draft.id))
            assertEquals(PendingOperation.DELETE, metadata.getPendingUploads(EntityType.DRAFT).single().operation)
        }

    @Test
    fun `a deletion queued after the storage write wins over recovery`() =
        runTest {
            val draft = EditorDraft()
            val drafts = FakeDraftRepository()
            val metadata =
                object : FakeSyncMetadataService() {
                    override suspend fun enqueueRestoreMutation(
                        entityId: String,
                        entityType: EntityType,
                        operation: PendingOperation,
                        scope: UploadScope?,
                    ): Boolean {
                        enqueuePending(entityId, entityType, PendingOperation.DELETE)
                        return false
                    }
                }
            val persistence = JournalDraftPersistence(drafts, metadata, { it }, {})
            assertFalse(persistence.compareAndSet(draft.id, null, draft))
            assertEquals(null, drafts.getDraft(draft.id))
            assertEquals(PendingOperation.DELETE, metadata.getPendingUploads(EntityType.DRAFT).single().operation)
        }
}
