package app.logdate.client.sync

import app.logdate.client.networking.DataUsageMode
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeDataUsagePolicy
import app.logdate.client.sync.test.fakeJournalNotesRepository
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MobileDataConsentTest {
    private val scope = UploadScope("owner", "origin")

    @Test
    fun `mobile data consent is scoped to one run and destination`() =
        runTest {
            assertFalse(mediaSyncAllowed(DataUsageMode.Conservative, scope))
            withMobileDataConsent(scope) {
                assertTrue(mediaSyncAllowed(DataUsageMode.Conservative, scope))
                assertFalse(mediaSyncAllowed(DataUsageMode.Conservative, scope.copy(ownerId = "other")))
                assertFalse(mediaSyncAllowed(DataUsageMode.Conservative, scope.copy(serverOrigin = "other")))
                assertFalse(mediaSyncAllowed(DataUsageMode.Restricted, scope))
            }
            assertFalse(mediaSyncAllowed(DataUsageMode.Conservative, scope))
        }

    @Test
    fun `requested run uploads deferred media and later automatic runs keep wifi policy`() =
        runTest {
            val api = fakeCloudApiClient()
            val notes = fakeJournalNotesRepository()
            val metadata = fakeSyncMetadataService()
            val sessions = fakeSessionStorage()
            val bound = sessions.getOriginBoundSession()!!
            val manager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(api),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(api),
                    journalNotesRepository = notes,
                    syncMetadataService = metadata,
                    sessionStorage = sessions,
                    dataUsagePolicy = fakeDataUsagePolicy(DataUsageMode.Conservative),
                    syncScope = backgroundScope,
                )
            val date = Instant.fromEpochMilliseconds(123)
            val note = JournalNote.Image(Uuid.random(), date, date, "file:///photo.jpg")
            notes.create(note)
            metadata.enqueuePending(note.uid.toString(), EntityType.NOTE, PendingOperation.CREATE)
            manager.syncContent()
            assertEquals(1, metadata.getPendingUploads(EntityType.NOTE).size)
            withMobileDataConsent(UploadScope(bound.session.accountId, bound.origin)) {
                assertTrue(manager.syncContent().success)
            }
            assertTrue(api.wasMethodCalled("uploadMedia"))
            assertTrue(metadata.getPendingUploads(EntityType.NOTE).isEmpty())
            val later = note.copy(uid = Uuid.random(), mediaRef = "file:///later.jpg")
            notes.create(later)
            metadata.enqueuePending(later.uid.toString(), EntityType.NOTE, PendingOperation.CREATE)
            manager.syncContent()
            assertEquals(1, metadata.getPendingUploads(EntityType.NOTE).size)
        }
}
