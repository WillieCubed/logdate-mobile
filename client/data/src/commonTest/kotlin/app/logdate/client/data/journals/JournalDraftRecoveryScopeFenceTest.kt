package app.logdate.client.data.journals

import app.logdate.client.data.fakes.FakeDraftRepository
import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.repository.journals.DraftRecoveryScope
import app.logdate.client.repository.journals.DraftRepository
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.shared.model.EditorDraft
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class JournalDraftRecoveryScopeFenceTest {
    @Test
    fun `anonymous outbox owner changes cannot write acknowledge or enqueue retained content`() =
        runTest {
            assertRejected(JournalMergeScope("other-owner", "https://one.example"))
        }

    @Test
    fun `anonymous outbox origin changes cannot write acknowledge or enqueue retained content`() =
        runTest {
            assertRejected(JournalMergeScope("owner", "https://other.example"))
        }

    @Test
    fun `anonymous owner change inside outbox admission rolls back before draft write`() =
        runTest {
            assertRejected(JournalMergeScope("other-owner", "https://one.example"), duringEnqueue = true)
        }

    @Test
    fun `anonymous origin change inside outbox admission rolls back before draft write`() =
        runTest {
            assertRejected(JournalMergeScope("owner", "https://other.example"), duringEnqueue = true)
        }

    private suspend fun assertRejected(
        changed: JournalMergeScope,
        duringEnqueue: Boolean = false,
    ) {
        val base = FakeDraftRepository()
        val current = EditorDraft()
        base.saveDraft(current)
        var outbox = JournalMergeScope("owner", "https://one.example")
        var receipts = 0
        val drafts =
            object : DraftRepository by base {
                override suspend fun getDraftRecoveryScope() = DraftRecoveryScope(null, null)

                override suspend fun getDraftForRestore(id: Uuid): EditorDraft? {
                    if (!duringEnqueue) outbox = changed
                    return base.getDraft(id)
                }

                override suspend fun compareAndSetDraftForRetainedRestore(
                    sourceId: Uuid,
                    targetId: Uuid,
                    expected: EditorDraft?,
                    replacement: EditorDraft?,
                    recoveryScope: DraftRecoveryScope?,
                    recoveryId: String?,
                ): Boolean {
                    receipts++
                    if (replacement != null) base.saveDraft(replacement)
                    return true
                }
            }
        var queued = 0
        val metadata =
            object : FakeSyncMetadataService() {
                override suspend fun enqueueRestoreMutation(
                    entityId: String,
                    entityType: app.logdate.client.sync.metadata.EntityType,
                    operation: app.logdate.client.sync.metadata.PendingOperation,
                    scope: app.logdate.client.sync.metadata.UploadScope?,
                ): Boolean {
                    queued++
                    if (duringEnqueue) outbox = changed
                    return true
                }
            }
        val transactions =
            object : app.logdate.client.sync.SyncTransactionManager {
                override suspend fun <T> withTransaction(block: suspend () -> T): T {
                    val before = queued
                    return try {
                        block()
                    } catch (failure: Throwable) {
                        queued = before
                        throw failure
                    }
                }
            }
        val persistence =
            JournalDraftPersistence(drafts, metadata, { it }, {}, transactions = transactions, currentOutboxScope = { outbox })
        val captured = persistence.captureScope { outbox }
        assertFailsWith<IllegalStateException> {
            persistence.compareAndSet(
                current.id,
                current,
                current.copy(lastModifiedAt = kotlin.time.Instant.DISTANT_FUTURE),
                captured,
                recoveryId = "retained",
                retainedSourceId = current.id,
            )
        }
        assertEquals(current, base.getDraft(current.id))
        assertEquals(0, receipts)
        assertEquals(0, queued)
        assertEquals(0, metadata.getPendingCount())
    }
}
