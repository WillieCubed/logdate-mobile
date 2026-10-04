package app.logdate.client.sync.recovery

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.InMemoryKeyValueStorage
import app.logdate.client.sync.cloud.DefaultCloudAssociationDataSource
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.InMemoryFirstSyncEnqueueStore
import app.logdate.client.sync.metadata.KeyValueFirstSyncEnqueueStore
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.test.FakeJournalContentRepository
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.fakeSyncMetadataService
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.Journal
import app.logdate.shared.model.sync.AssociationChangesResponse
import app.logdate.shared.model.sync.AssociationDeletion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class LegacyMembershipAuditTest {
    @Test
    fun `failed download and interrupted backfill retry without losing queued memberships`() =
        runTest {
            val journal = Journal()
            val notes =
                (1..2).map {
                    JournalNote.Text(
                        uid = Uuid.random(),
                        creationTimestamp = Instant.fromEpochMilliseconds(1),
                        lastUpdated = Instant.fromEpochMilliseconds(1),
                        content = "local",
                    )
                }
            val localNotes = FakeJournalNotesRepository().apply { notes.forEach { create(it) } }
            val contentRepository =
                object : JournalContentRepository by FakeJournalContentRepository() {
                    override fun observeJournalsForContents(contentIds: Set<Uuid>): Flow<Map<Uuid, List<Journal>>> =
                        flowOf(notes.filter { it.uid in contentIds }.associate { it.uid to listOf(journal) })
                }
            val queue = fakeSyncMetadataService()
            var shouldInterrupt = true
            val interruptibleQueue =
                object : SyncMetadataService by queue {
                    override suspend fun enqueueRepairIfAbsent(
                        entityId: String,
                        entityType: EntityType,
                        expectedServerVersion: Long?,
                        serverOrigin: String?,
                    ) {
                        if (shouldInterrupt && queue.getPendingUploads(EntityType.ASSOCIATION).size == 1) {
                            throw IllegalStateException("Interrupted backfill")
                        }
                        queue.enqueueRepairIfAbsent(entityId, entityType, expectedServerVersion, serverOrigin)
                    }
                }
            val api =
                fakeCloudApiClient().apply {
                    getAssociationChangesResponse = Result.failure(IllegalStateException("Unavailable"))
                }
            val db = DownloadInboxTest.Database()
            val inbox = DownloadInbox(db, db, { DownloadScope("owner", "origin") }, { 1L })
            val firstSync = InMemoryFirstSyncEnqueueStore()

            fun manager() =
                testDefaultSyncManager(
                    journalNotesRepository = localNotes,
                    journalContentRepository = contentRepository,
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(api),
                    syncMetadataService = interruptibleQueue,
                    downloadInbox = inbox,
                    firstSyncEnqueueStore = firstSync,
                    syncScope = backgroundScope,
                )

            assertFalse(manager().downloadRemoteChanges().success)
            assertTrue(queue.getPendingUploads(EntityType.ASSOCIATION).isEmpty())
            assertFalse(firstSync.hasEnqueuedAssociationScope("owner", "origin"))

            api.getAssociationChangesResponse = Result.success(AssociationChangesResponse(emptyList(), emptyList(), 10))
            assertFalse(manager().downloadRemoteChanges().success)
            assertEquals(1, queue.getPendingUploads(EntityType.ASSOCIATION).size)
            assertFalse(firstSync.hasEnqueuedAssociationScope("owner", "origin"))

            shouldInterrupt = false
            assertTrue(manager().downloadRemoteChanges().success)
            assertEquals(
                notes.map { AssociationPendingKey(journal.id, it.uid).toPendingId() }.toSet(),
                queue.getPendingUploads(EntityType.ASSOCIATION).map { it.entityId }.toSet(),
            )
            assertTrue(firstSync.hasEnqueuedAssociationScope("owner", "origin"))
        }

    @Test
    fun `membership backfill marker survives recreation and belongs to one account and server`() =
        runTest {
            val backing = InMemoryKeyValueStorage()
            val storage =
                object : KeyValueStorage by backing {
                    override suspend fun getBoolean(
                        key: String,
                        defaultValue: Boolean,
                    ): Boolean = backing.getString(key)?.toBooleanStrictOrNull() ?: defaultValue

                    override suspend fun putBoolean(
                        key: String,
                        value: Boolean,
                    ) = backing.putString(key, value.toString())
                }
            KeyValueFirstSyncEnqueueStore(storage).markEnqueuedAssociationScope("owner", "origin")
            val restored = KeyValueFirstSyncEnqueueStore(storage)
            assertTrue(restored.hasEnqueuedAssociationScope("owner", "origin"))
            assertFalse(restored.hasEnqueuedAssociationScope("other-owner", "origin"))
            assertFalse(restored.hasEnqueuedAssociationScope("owner", "other-origin"))
            assertFalse(restored.hasAuditedLegacyScope("owner", "origin"))
        }

    @Test
    fun `historical memberships are queued once after remote removals and preserve pending deletion`() =
        runTest {
            val journal = Journal()
            val notes =
                (1..3).map {
                    JournalNote.Text(
                        uid = Uuid.random(),
                        creationTimestamp = Instant.fromEpochMilliseconds(1),
                        lastUpdated = Instant.fromEpochMilliseconds(1),
                        content = "local",
                    )
                }
            val localNotes = FakeJournalNotesRepository().apply { notes.forEach { create(it) } }
            var memberships = notes.associate { it.uid to listOf(journal) }
            val contentRepository =
                object : JournalContentRepository by FakeJournalContentRepository() {
                    override fun observeJournalsForContents(contentIds: Set<Uuid>): Flow<Map<Uuid, List<Journal>>> =
                        flowOf(
                            memberships.filterKeys {
                                it in
                                    contentIds
                            },
                        )

                    override suspend fun removeContentFromJournal(
                        contentId: Uuid,
                        journalId: Uuid,
                    ) {
                        memberships = memberships - contentId
                    }
                }
            val queue = fakeSyncMetadataService()
            val removedRemotely = notes[1].uid
            val pendingRemoval = AssociationPendingKey(journal.id, notes[2].uid).toPendingId()
            queue.enqueuePending(pendingRemoval, EntityType.ASSOCIATION, PendingOperation.DELETE)
            val api =
                fakeCloudApiClient().apply {
                    getAssociationChangesResponse =
                        Result.success(
                            AssociationChangesResponse(
                                emptyList(),
                                listOf(AssociationDeletion(journal.id.toString(), removedRemotely.toString(), 10)),
                                10,
                            ),
                        )
                }
            val db = DownloadInboxTest.Database()
            val inbox = DownloadInbox(db, db, { DownloadScope("owner", "origin") }, { 1L })
            val firstSync = InMemoryFirstSyncEnqueueStore()

            fun manager() =
                testDefaultSyncManager(
                    journalNotesRepository = localNotes,
                    journalContentRepository = contentRepository,
                    cloudAssociationDataSource = DefaultCloudAssociationDataSource(api),
                    syncMetadataService = queue,
                    downloadInbox = inbox,
                    firstSyncEnqueueStore = firstSync,
                    syncScope = backgroundScope,
                )

            manager().downloadRemoteChanges()
            val pending = queue.getPendingUploads(EntityType.ASSOCIATION).associateBy { it.entityId }
            assertEquals(setOf(AssociationPendingKey(journal.id, notes[0].uid).toPendingId(), pendingRemoval), pending.keys)
            assertEquals(PendingOperation.DELETE, pending.getValue(pendingRemoval).operation)
            assertFalse(AssociationPendingKey(journal.id, removedRemotely).toPendingId() in pending)

            val later =
                JournalNote.Text(
                    uid = Uuid.random(),
                    creationTimestamp = Instant.fromEpochMilliseconds(1),
                    lastUpdated = Instant.fromEpochMilliseconds(1),
                    content = "later",
                )
            localNotes.create(later)
            memberships = memberships + (later.uid to listOf(journal))
            manager().downloadRemoteChanges()
            assertEquals(pending.keys, queue.getPendingUploads(EntityType.ASSOCIATION).map { it.entityId }.toSet())
        }
}
