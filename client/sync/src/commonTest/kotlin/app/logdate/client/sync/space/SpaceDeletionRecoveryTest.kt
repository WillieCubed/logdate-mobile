package app.logdate.client.sync.space

import app.logdate.client.device.crypto.IdentityKeyNotFoundException
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalContentRepository
import app.logdate.client.sync.SyncPausedReason
import app.logdate.client.sync.SyncStorageUnavailableException
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.conflict.SyncConflictRecord
import app.logdate.client.sync.conflict.SyncConflictStore
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.recovery.DownloadInbox
import app.logdate.client.sync.recovery.DownloadInboxTest
import app.logdate.client.sync.recovery.DownloadScope
import app.logdate.client.sync.space.mappers.DraftRecordMapper
import app.logdate.client.sync.space.mappers.JournalMembership
import app.logdate.client.sync.space.mappers.MembershipRecordMapper
import app.logdate.client.sync.space.mappers.SpaceDraft
import app.logdate.client.sync.space.mappers.draftSpaceKind
import app.logdate.client.sync.space.mappers.membershipSpaceKind
import app.logdate.client.sync.test.FakeJournalContentRepository
import app.logdate.client.sync.test.FakeJournalNotesRepository
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.client.sync.test.FakeSessionStorage
import app.logdate.client.sync.test.FakeSyncMetadataService
import app.logdate.client.sync.test.InMemorySyncConflictStore
import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.AccountStorageMode
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import app.logdate.shared.model.SerializableTextBlock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SpaceDeletionRecoveryTest {
    @Test
    fun `a space draft deletion preserves queued edits and settles matching local deletes`() =
        runTest {
            for (operation in PendingOperation.entries) {
                val device = Device()
                device.receiveDraft()
                device.metadata.enqueuePending(device.draft.id.toString(), EntityType.DRAFT, operation)
                device.home.deleteFromAnotherDevice(DRAFT_COLLECTION, device.draft.id.toString())

                val result = device.manager.syncDrafts()

                assertTrue(result.success, result.errors.toString())
                assertNull(device.drafts.getDraft(device.draft.id))
                assertTrue(device.metadata.getPendingUploads(EntityType.DRAFT).isEmpty())
                if (operation != PendingOperation.DELETE) {
                    assertEquals(
                        device.draft,
                        device.conflicts
                            .list()
                            .single()
                            .deletedEdit
                            ?.draft,
                    )
                } else {
                    assertTrue(device.conflicts.list().isEmpty())
                }
                assertEquals(0, device.inbox.count())
                assertNull(device.swaps.get(device.scope, DRAFT_COLLECTION, device.draft.id.toString()))
            }
        }

    @Test
    fun `a space membership deletion preserves queued edits and settles matching local deletes`() =
        runTest {
            for (operation in PendingOperation.entries) {
                val device = Device()
                device.receiveMembership()
                device.metadata.enqueuePending(device.membershipKey, EntityType.ASSOCIATION, operation)
                device.home.deleteFromAnotherDevice(MEMBERSHIP_COLLECTION, device.membershipRkey)

                val result = device.manager.syncAssociations()

                assertTrue(result.success, result.errors.toString())
                assertFalse(device.links.contains(device.journalId, device.note.uid))
                assertTrue(device.metadata.getPendingUploads(EntityType.ASSOCIATION).isEmpty())
                if (operation != PendingOperation.DELETE) {
                    val retained =
                        assertNotNull(
                            device.conflicts
                                .list()
                                .single()
                                .deletedEdit,
                        )
                    assertEquals(device.note, retained.note)
                    assertEquals(listOf(device.journalId.toString()), retained.journalIds)
                } else {
                    assertTrue(device.conflicts.list().isEmpty())
                }
                assertEquals(0, device.inbox.count())
                assertNull(device.swaps.get(device.scope, MEMBERSHIP_COLLECTION, device.membershipRkey))
            }
        }

    @Test
    fun `a failed preservation leaves the draft or membership and its outbox operation available for retry`() =
        runTest {
            for (draft in listOf(true, false)) {
                val device = Device()
                if (draft) device.receiveDraft() else device.receiveMembership()
                val type = if (draft) EntityType.DRAFT else EntityType.ASSOCIATION
                val id = if (draft) device.draft.id.toString() else device.membershipKey
                device.metadata.enqueuePending(id, type, PendingOperation.UPDATE)
                device.conflicts.failWrites = true
                device.home.deleteFromAnotherDevice(
                    if (draft) DRAFT_COLLECTION else MEMBERSHIP_COLLECTION,
                    if (draft) id else device.membershipRkey,
                )

                val result = if (draft) device.manager.syncDrafts() else device.manager.syncAssociations()

                assertFalse(result.success)
                if (draft) {
                    assertNotNull(device.drafts.getDraft(device.draft.id))
                } else {
                    assertTrue(device.links.contains(device.journalId, device.note.uid))
                }
                assertEquals(
                    id,
                    device.metadata
                        .getPendingUploads(type)
                        .single()
                        .entityId,
                )
                assertTrue(device.database.rows.any { it.entityType == spacePartsType(type) && it.state != "APPLIED" })

                device.conflicts.failWrites = false
                device.inbox.release()
                val retry = if (draft) device.manager.syncDrafts() else device.manager.syncAssociations()
                assertTrue(retry.success, retry.errors.toString())
                assertTrue(device.metadata.getPendingUploads(type).isEmpty())
                assertNotNull(
                    device.conflicts
                        .list()
                        .single()
                        .deletedEdit,
                )
            }
        }

    @Test
    fun `device failures in all four space record handlers pause the pass without charging the row`() =
        runTest {
            for (draft in listOf(true, false)) {
                for (deletion in listOf(true, false)) {
                    for (missingIdentity in listOf(true, false)) {
                        val device = Device()
                        if (deletion) {
                            if (draft) device.receiveDraft() else device.receiveMembership()
                            device.home.deleteFromAnotherDevice(
                                if (draft) DRAFT_COLLECTION else MEMBERSHIP_COLLECTION,
                                if (draft) device.draft.id.toString() else device.membershipRkey,
                            )
                        } else {
                            if (draft) device.writeDraft() else device.writeMembership()
                        }
                        device.transactions.failure =
                            if (missingIdentity) IdentityKeyNotFoundException("Identity unavailable") else SyncStorageUnavailableException()

                        val result = if (draft) device.manager.syncDrafts() else device.manager.syncAssociations()

                        assertFalse(result.success)
                        assertEquals(
                            if (missingIdentity) SyncPausedReason.NEEDS_RECOVERY_PHRASE else SyncPausedReason.LOCAL_STORAGE_UNAVAILABLE,
                            device.manager.readinessGate.reason.value,
                        )
                        val type = spacePartsType(if (draft) EntityType.DRAFT else EntityType.ASSOCIATION)
                        val row = device.database.rows.single { it.entityType == type && it.state != "APPLIED" }
                        assertEquals(0, row.attempts)
                        assertEquals("WAITING", row.state)
                    }
                }
            }
        }

    private class Device {
        val home = FakeDataHome()
        val drafts = FakeJournalRepository()
        val notes = FakeJournalNotesRepository()
        val links = Links()
        val sessions = FakeSessionStorage()
        val metadata = FakeSyncMetadataService(sessions, trackOperationIdentity = true, currentScope = { scope })
        val conflicts = Conflicts()
        val database = DownloadInboxTest.Database()
        val transactions = Transactions()
        val inbox = DownloadInbox(database, database, { DownloadScope("test-account-id", sessions.currentOrigin) }, { 0L })
        val swaps = InMemorySpaceSwapStore()
        val scope get() =
            app.logdate.client.sync.metadata
                .UploadScope("test-account-id", sessions.currentOrigin)
        val time = Instant.parse("2026-10-01T09:00:00Z")
        val draft =
            EditorDraft(
                id = Uuid.random(),
                blocks = listOf(SerializableTextBlock(Uuid.random(), time, content = "Keep this draft")),
                createdAt = time,
                lastModifiedAt = time,
            )
        val journalId = Uuid.random()
        val note =
            JournalNote.Text(
                uid = Uuid.random(),
                content = "Keep this entry",
                creationTimestamp = time,
                lastUpdated = time,
            )
        val membershipKey = AssociationPendingKey(journalId, note.uid).toPendingId()
        val membershipRkey = MembershipRecordMapper.rkeyFor(journalId, note.uid)
        val manager =
            testDefaultSyncManager(
                journalRepository = drafts,
                journalNotesRepository = notes,
                journalContentRepository = links,
                syncMetadataService = metadata,
                sessionStorage = sessions,
                conflictStore = conflicts,
                transactionManager = transactions,
                downloadInbox = inbox,
                spaceSync =
                    SpaceSyncDependencies(
                        registry = SpaceRecordRegistry(listOf(draftSpaceKind(drafts), membershipSpaceKind(links))),
                        gate = SpaceTransportGate({ true }, { true }, { AccountStorageMode.PERSONAL_SPACE_V1 }),
                        homes = SpaceDataHomes { _, token -> home.signedIn(token) },
                        swapStore = swaps,
                        accountDid = { TEST_DID },
                        identityKey = { TEST_IDENTITY_KEY },
                    ),
            )

        suspend fun writeDraft() {
            home.putFromAnotherDevice(DRAFT_COLLECTION, draft.id.toString(), DraftRecordMapper().toRecord(SpaceDraft(draft)))
        }

        suspend fun receiveDraft() {
            writeDraft()
            assertTrue(manager.syncDrafts().success)
        }

        suspend fun writeMembership() {
            home.putFromAnotherDevice(
                MEMBERSHIP_COLLECTION,
                membershipRkey,
                MembershipRecordMapper().toRecord(JournalMembership(journalId, note.uid)),
            )
        }

        suspend fun receiveMembership() {
            notes.create(note)
            writeMembership()
            assertTrue(manager.syncAssociations().success)
        }
    }

    private class Links :
        JournalContentRepository by FakeJournalContentRepository(),
        SyncableJournalContentRepository {
        private val pairs = mutableSetOf<Pair<Uuid, Uuid>>()

        fun contains(
            journal: Uuid,
            note: Uuid,
        ) = journal to note in pairs

        override fun observeJournalsForContent(contentId: Uuid) =
            flowOf(pairs.filter { it.second == contentId }.map { Journal(id = it.first, title = "Journal") })

        override suspend fun addContentToJournalFromSync(
            contentId: Uuid,
            journalId: Uuid,
        ) {
            pairs += journalId to contentId
        }

        override suspend fun removeContentFromJournalFromSync(
            contentId: Uuid,
            journalId: Uuid,
        ) {
            pairs -= journalId to contentId
        }
    }

    private class Conflicts(
        private val records: InMemorySyncConflictStore = InMemorySyncConflictStore(),
    ) : SyncConflictStore by records {
        var failWrites = false

        override suspend fun add(record: SyncConflictRecord) {
            check(!failWrites) { "Recovery store unavailable" }
            records.add(record)
        }
    }

    private class Transactions : SyncTransactionManager {
        var failure: Exception? = null

        override suspend fun <T> withTransaction(block: suspend () -> T): T {
            failure?.let { throw it }
            return block()
        }
    }

    private companion object {
        const val DRAFT_COLLECTION = "app.logdate.journal.draft"
        const val MEMBERSHIP_COLLECTION = "app.logdate.journal.membership"
    }
}
