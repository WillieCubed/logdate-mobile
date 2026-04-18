package app.logdate.client.sync.journey

import app.logdate.client.datastore.UserSession
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.cloud.DefaultCloudDraftDataSource
import app.logdate.client.sync.cloud.DefaultCloudJournalDataSource
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.test.fakeAccountRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeJournalContentRepository
import app.logdate.client.sync.test.fakeJournalNotesRepository
import app.logdate.client.sync.test.fakeJournalRepository
import app.logdate.client.sync.test.fakeSessionStorage
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.Journal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Invariant: data created while signed-out drains to the cloud once a session appears.
 *
 * The server can't distinguish a backlog upload from any other upload, so the launch-critical
 * question is client-side: when the local DB has N pending items created accountless and a session
 * is saved, does `SyncManager.fullSync()` pick them up and push them? This test seeds a backlog,
 * flips the fakes from unauthenticated to authenticated, and verifies every queued entity reaches
 * the fake `CloudApiClient`.
 */
class AccountlessToCloudJourneyTest {
    @Test
    fun backlog_created_accountless_drains_after_first_session() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val sessionStorage = fakeSessionStorage(authenticated = false)
            val accountRepository = fakeAccountRepository(authenticated = false)
            val syncMetadataService = fakeSyncMetadataService()
            val notesRepository = fakeJournalNotesRepository()
            val journalRepository = fakeJournalRepository()

            val syncManager =
                testDefaultSyncManager(
                    cloudContentDataSource = DefaultCloudContentDataSource(apiClient),
                    cloudJournalDataSource = DefaultCloudJournalDataSource(apiClient),
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(apiClient),
                    cloudMediaDataSource = DefaultCloudMediaDataSource(apiClient),
                    cloudDraftDataSource = DefaultCloudDraftDataSource(apiClient),
                    cloudAccountRepository = accountRepository,
                    sessionStorage = sessionStorage,
                    journalRepository = journalRepository,
                    journalNotesRepository = notesRepository,
                    journalContentRepository = fakeJournalContentRepository(),
                    syncMetadataService = syncMetadataService,
                )

            val notes = (1..5).map { notesRepository.addTestNote("accountless note $it") }
            val journals =
                (1..3).map {
                    Journal(
                        id = Uuid.random(),
                        title = "accountless journal $it",
                        description = "seeded before sign-in",
                        created = Clock.System.now(),
                        lastUpdated = Clock.System.now(),
                    ).also { journalRepository.create(it) }
                }
            val associationKeys =
                (1..4).map { AssociationPendingKey(journalId = Uuid.random(), contentId = Uuid.random()) }

            notes.forEach { note ->
                syncMetadataService.enqueuePending(
                    entityId = note.uid.toString(),
                    entityType = EntityType.NOTE,
                    operation = PendingOperation.CREATE,
                )
            }
            journals.forEach { journal ->
                syncMetadataService.enqueuePending(
                    entityId = journal.id.toString(),
                    entityType = EntityType.JOURNAL,
                    operation = PendingOperation.CREATE,
                )
            }
            associationKeys.forEach { key ->
                syncMetadataService.enqueuePending(
                    entityId = key.toPendingId(),
                    entityType = EntityType.ASSOCIATION,
                    operation = PendingOperation.CREATE,
                )
            }

            sessionStorage.saveSession(
                UserSession(
                    accessToken = "drained-access-token",
                    refreshToken = "drained-refresh-token",
                    accountId = Uuid.random().toString(),
                ),
            )
            accountRepository.setAuthenticated(true)

            val result = syncManager.fullSync()

            assertTrue(result.success, "fullSync should succeed once a session exists: ${result.errors}")
            assertTrue(
                apiClient.uploadContentCalls.size >= notes.size,
                "Every accountless note should upload (got ${apiClient.uploadContentCalls.size} of ${notes.size})",
            )
            assertTrue(
                apiClient.wasMethodCalled("uploadJournal"),
                "Every accountless journal should upload",
            )
            assertTrue(
                apiClient.wasMethodCalled("uploadAssociations"),
                "Every accountless association should upload",
            )
            assertTrue(
                syncMetadataService.getPendingUploads(EntityType.NOTE).isEmpty(),
                "Pending note queue should drain after a successful fullSync",
            )
            assertTrue(
                syncMetadataService.getPendingUploads(EntityType.JOURNAL).isEmpty(),
                "Pending journal queue should drain after a successful fullSync",
            )
            assertTrue(
                syncMetadataService.getPendingUploads(EntityType.ASSOCIATION).isEmpty(),
                "Pending association queue should drain after a successful fullSync",
            )
        }
}
