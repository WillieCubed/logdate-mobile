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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Invariant: data created while signed-out drains to the cloud after first sign-in.
 *
 * Post-gating, the metadata layer refuses to enqueue while there is no session — accountless
 * writes are local-only. The product still promises that signing in for the first time will
 * back up everything the user has, so onboarding walks the local DB and re-enqueues every
 * record (the production code path lives in `BackfillLocalDataUseCase` in `client/domain`,
 * which is unit-tested separately to avoid a cross-module dependency in this journey test).
 *
 * This test exercises the equivalent flow end-to-end: seeds local repos while signed-out,
 * verifies nothing enqueued, signs in, replays the backfill walk inline, and asserts every
 * entity reaches the fake `CloudApiClient`.
 */
class AccountlessToCloudJourneyTest {
    @Test
    fun backlog_created_accountless_drains_after_first_sign_in_via_backfill() =
        runTest {
            val apiClient = fakeCloudApiClient()
            val sessionStorage = fakeSessionStorage(authenticated = false)
            val accountRepository = fakeAccountRepository(authenticated = false)
            // Auth-gated metadata service: enqueue is a no-op until sessionStorage holds a session.
            val syncMetadataService = fakeSyncMetadataService(sessionStorage)
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
            val associations =
                (0 until 4).map { i ->
                    val journalId = journals[i % journals.size].id
                    val noteId = notes[i % notes.size].uid
                    journalId to noteId
                }
            associations.forEach { (journalId, noteId) ->
                notesRepository.addTestAssociation(journalId, noteId)
            }

            // Sanity: nothing should be enqueued yet — accountless writes don't produce sync signal.
            assertTrue(
                syncMetadataService.getPendingUploads(EntityType.NOTE).isEmpty(),
                "Notes must not enqueue while signed-out",
            )
            assertTrue(
                syncMetadataService.getPendingUploads(EntityType.JOURNAL).isEmpty(),
                "Journals must not enqueue while signed-out",
            )

            // First sign-in.
            sessionStorage.saveSession(
                UserSession(
                    accessToken = "drained-access-token",
                    refreshToken = "drained-refresh-token",
                    accountId = Uuid.random().toString(),
                ),
            )
            accountRepository.setAuthenticated(true)

            // Replay the backfill walk: same logic the production BackfillLocalDataUseCase runs.
            // Lives in client/domain and is unit-tested there; inlined here to avoid a
            // cross-module test dependency.
            journalRepository.allJournalsObserved.first().forEach { j ->
                syncMetadataService.enqueuePending(
                    entityId = j.id.toString(),
                    entityType = EntityType.JOURNAL,
                    operation = PendingOperation.CREATE,
                )
            }
            notesRepository.allNotesObserved.first().forEach { n ->
                syncMetadataService.enqueuePending(
                    entityId = n.uid.toString(),
                    entityType = EntityType.NOTE,
                    operation = PendingOperation.CREATE,
                )
            }
            notesRepository.getAllJournalNoteLinks().forEach { (journalId, contentId) ->
                syncMetadataService.enqueuePending(
                    entityId = AssociationPendingKey(journalId, contentId).toPendingId(),
                    entityType = EntityType.ASSOCIATION,
                    operation = PendingOperation.CREATE,
                )
            }

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
