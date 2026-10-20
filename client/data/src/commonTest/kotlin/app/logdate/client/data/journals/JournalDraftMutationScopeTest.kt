package app.logdate.client.data.journals

import app.logdate.client.data.fakes.FakeDraftRepository
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.repository.journals.DraftRecoveryScope
import app.logdate.client.repository.journals.DraftRepository
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.model.EditorDraft
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class JournalDraftMutationScopeTest {
    @Test
    fun `save queues the durable owner and server when selection changes after writing`() =
        runTest {
            val f = Fixture()
            f.afterWrite = { f.selected = f.other }
            f.persistence().save(f.draft)
            assertEquals(listOf(f.first to PendingOperation.UPDATE), f.queued)
            assertEquals(f.draft, f.stores.getValue(f.first).getDraft(f.draft.id))
            assertEquals(null, f.stores.getValue(f.other).getDraft(f.draft.id))
        }

    @Test
    fun `delete cannot queue a deletion against the newly selected server`() =
        runTest {
            val f = Fixture()
            f.stores.getValue(f.first).saveDraft(f.draft)
            f.afterWrite = { f.selected = f.other }
            f.persistence().delete(f.draft.id)
            assertEquals(listOf(f.first to PendingOperation.DELETE), f.queued)
            assertEquals(null, f.stores.getValue(f.first).getDraft(f.draft.id))
        }

    @Test
    fun `selection changing during journal resolution cannot save into the new scope`() =
        runTest {
            val f = Fixture()
            assertFailsWith<IllegalStateException> {
                f
                    .persistence {
                        f.selected = f.other
                        it
                    }.save(f.draft)
            }
            assertEquals(emptyList(), f.stores.getValue(f.other).getAllDrafts())
            assertEquals(emptyList(), f.queued)
        }

    private class Fixture {
        val first = UploadScope("journal-owner", "https://first.example")
        val other = UploadScope("journal-owner", "https://other.example")
        var selected = first
        val stores = mapOf(first to FakeDraftRepository(), other to FakeDraftRepository())
        val draft = EditorDraft()
        val queued = mutableListOf<Pair<UploadScope, PendingOperation>>()
        var afterWrite: () -> Unit = {}
        private val drafts =
            object : DraftRepository by stores.getValue(first) {
                override suspend fun getDraftRecoveryScope() = DraftRecoveryScope(selected.ownerId, selected.serverOrigin)

                override suspend fun getDraftForRestore(id: Uuid) = stores.getValue(selected).getDraft(id)

                override suspend fun saveDraft(draft: EditorDraft) {
                    stores.getValue(selected).saveDraft(draft)
                    afterWrite()
                }

                override suspend fun deleteDraft(id: Uuid) {
                    stores.getValue(selected).deleteDraft(id)
                    afterWrite()
                }

                override suspend fun compareAndSetDraftForRestore(
                    id: Uuid,
                    expected: EditorDraft?,
                    replacement: EditorDraft?,
                    recoveryScope: DraftRecoveryScope?,
                    recoveryId: String?,
                ): Boolean {
                    check(recoveryScope?.ownerId == selected.ownerId && recoveryScope.serverOrigin == selected.serverOrigin)
                    val store = stores.getValue(selected)
                    if (store.getDraft(id) != expected) return false
                    if (replacement == null) store.deleteDraft(id) else store.saveDraft(replacement)
                    afterWrite()
                    return true
                }
            }
        private val metadata =
            object : FakeSyncMetadataService() {
                override suspend fun enqueuePending(
                    entityId: String,
                    entityType: EntityType,
                    operation: PendingOperation,
                ) {
                    queued += selected to operation
                }

                override suspend fun enqueuePendingForScope(
                    entityId: String,
                    entityType: EntityType,
                    operation: PendingOperation,
                    scope: UploadScope,
                ) {
                    queued += scope to operation
                }
            }

        fun persistence(resolve: suspend (EditorDraft) -> EditorDraft = { it }) =
            JournalDraftPersistence(
                drafts,
                metadata,
                resolve,
                {},
                currentOutboxScope = { JournalMergeScope(selected.ownerId, selected.serverOrigin) },
            )
    }
}
