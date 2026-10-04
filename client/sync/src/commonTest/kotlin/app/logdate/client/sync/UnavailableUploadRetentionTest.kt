package app.logdate.client.sync

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

class UnavailableUploadRetentionTest {
    @Test
    fun `an unavailable local record is retained rather than reported as backed up`() =
        runTest {
            for (type in listOf(EntityType.NOTE, EntityType.JOURNAL)) {
                val metadata = fakeSyncMetadataService()
                metadata.enqueuePending(Uuid.random().toString(), type, PendingOperation.UPDATE)
                val pending = metadata.getPendingUploads(type).single()
                val manager = testDefaultSyncManager(syncMetadataService = metadata, syncScope = backgroundScope)

                val result = manager.uploadPendingChanges()

                assertFalse(result.success)
                assertEquals(pending.entityId, metadata.getPendingUploads(type).single().entityId)
                assertEquals(pending.operation, metadata.getPendingUploads(type).single().operation)
            }
        }

    @Test
    fun `an unrecognized legacy identifier retains its operation for later compatibility repair`() =
        runTest {
            val metadata = fakeSyncMetadataService()
            metadata.enqueuePending("legacy-identifier", EntityType.NOTE, PendingOperation.CREATE)
            val manager = testDefaultSyncManager(syncMetadataService = metadata, syncScope = backgroundScope)

            assertFalse(manager.uploadPendingChanges().success)
            assertEquals("legacy-identifier", metadata.getPendingUploads(EntityType.NOTE).single().entityId)
        }
}
