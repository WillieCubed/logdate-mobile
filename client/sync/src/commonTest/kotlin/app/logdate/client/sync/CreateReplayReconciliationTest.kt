package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.sync.ContentChange
import app.logdate.shared.model.sync.ContentChangesResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class CreateReplayReconciliationTest {
    @Test
    fun `a create whose acknowledgement was lost is settled from its matching remote record`() = reconcile(PendingOperation.CREATE)

    @Test
    fun `an update whose acknowledgement was lost is settled without another write or conflict`() = reconcile(PendingOperation.UPDATE)

    @Test
    fun `an older matching remote record cannot retire a newer pending edit`() =
        reconcile(PendingOperation.UPDATE, remoteVersion = 40, acknowledge = false)

    private fun reconcile(
        operation: PendingOperation,
        remoteVersion: Long = 42,
        acknowledge: Boolean = true,
    ) = runTest {
        val local =
            JournalNote.Text(
                Uuid.random(),
                Instant.parse("1970-01-01T00:00:00.001123456Z"),
                Instant.parse("1970-01-01T00:00:00.002654321Z"),
                "Original entry",
                timeZoneId = "America/Los_Angeles",
                syncVersion = if (operation == PendingOperation.UPDATE) 41 else 0,
            )
        val notes = FakeJournalNotesRepository().apply { create(local) }
        val metadata = fakeSyncMetadataService()
        metadata.enqueuePending(local.uid.toString(), EntityType.NOTE, operation)
        val api =
            fakeCloudApiClient().apply {
                getContentChangesResponse =
                    Result.success(
                        ContentChangesResponse(
                            listOf(
                                ContentChange(
                                    local.uid.toString(),
                                    "TEXT",
                                    local.content,
                                    createdAt = 1,
                                    lastUpdated = 2,
                                    serverVersion = remoteVersion,
                                ),
                            ),
                            emptyList(),
                            remoteVersion,
                        ),
                    )
            }
        val manager =
            testDefaultSyncManager(
                cloudContentDataSource = DefaultCloudContentDataSource(api),
                journalNotesRepository = notes,
                syncMetadataService = metadata,
                syncScope = backgroundScope,
            )

        assertTrue(if (acknowledge) manager.syncContent().success else manager.downloadRemoteChanges().success)

        assertTrue(api.uploadContentCalls.isEmpty(), "A lost acknowledgement must not replay a blind overwrite")
        assertTrue("updateContent" !in api.methodCalls, "A matching remote update already satisfies the queued edit")
        assertEquals(acknowledge, metadata.getPendingUploads(EntityType.NOTE).isEmpty())
        if (!acknowledge) assertEquals(operation, metadata.getPendingUploads(EntityType.NOTE).single().operation)
        assertEquals(local.content, (notes.getNoteById(local.uid) as JournalNote.Text).content)
        assertEquals(local.creationTimestamp, notes.getNoteById(local.uid)?.creationTimestamp)
        assertEquals(local.lastUpdated, notes.getNoteById(local.uid)?.lastUpdated)
        assertEquals(local.timeZoneId, notes.getNoteById(local.uid)?.timeZoneId)
        assertEquals(if (acknowledge) remoteVersion else local.syncVersion, notes.getNoteById(local.uid)?.syncVersion)
        assertEquals(0, manager.getSyncStatus().conflictCount)
    }
}
