package app.logdate.client.data.journals

import app.logdate.client.data.fakes.FakeDraftRepository
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.shared.model.EditorDraft
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JournalDraftRecoveryIntentTest {
    @Test
    fun `recovery enqueue preserves a later pending deletion`() =
        runTest {
            val repository = FakeDraftRepository()
            val metadata = FakeSyncMetadataService()
            val draft = EditorDraft()
            repository.saveDraft(draft)
            metadata.enqueuePending(draft.id.toString(), EntityType.DRAFT, PendingOperation.DELETE)
            JournalDraftPersistence(repository, metadata, { it }, {}).ensureRecoveryQueued(draft.id, null)
            assertEquals(listOf(PendingOperation.DELETE), metadata.getPendingUploads(EntityType.DRAFT).map { it.operation })
        }

    @Test
    fun `replay repairs missing upload intent after persistence completed before enqueue`() =
        runTest {
            val repository = FakeDraftRepository()
            val metadata =
                object : FakeSyncMetadataService() {
                    var failOnce = true

                    override suspend fun enqueuePending(
                        entityId: String,
                        entityType: EntityType,
                        operation: PendingOperation,
                    ) {
                        if (failOnce) {
                            failOnce = false
                            error("Interrupted before enqueue")
                        }
                        super.enqueuePending(entityId, entityType, operation)
                    }
                }
            val imported = EditorDraft()
            val persistence = JournalDraftPersistence(repository, metadata, { it }, {})
            assertFailsWith<IllegalStateException> { persistence.compareAndSet(imported.id, null, imported, null) }
            assertEquals(imported, repository.getDraft(imported.id))
            assertEquals(emptyList(), metadata.getPendingUploads(EntityType.DRAFT))
            persistence.ensureRecoveryQueued(imported.id, null)
            assertEquals(imported, repository.getDraft(imported.id))
            assertEquals(listOf(imported.id.toString()), metadata.getPendingUploads(EntityType.DRAFT).map { it.entityId })
            persistence.ensureRecoveryQueued(imported.id, null)
            assertEquals(1, metadata.getPendingUploads(EntityType.DRAFT).size)
        }
}
