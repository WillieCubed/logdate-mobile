package app.logdate.client.sync.space

import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.conflict.ConflictResolution
import app.logdate.client.sync.conflict.ConflictResolver
import app.logdate.client.sync.conflict.DeletionResolution
import app.logdate.client.sync.conflict.LastWriteWinsResolver
import app.logdate.client.sync.test.FakeJournalRepository
import app.logdate.shared.model.Journal
import app.logdate.shared.model.diagnostics.DiagnosticReason
import app.logdate.space.SealedRecordCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** A deletion is never lost, whatever state this device holds the record in when it arrives. */
class SpaceDeletionTest {
    private val later = 24L * 60 * 60 * 1000

    @Test
    fun `a record deleted while unreadable never comes back once its key is recovered`() =
        runTest {
            val home = FakeDataHome()
            val device = SpaceDevice(home).apply { identityKey = ByteArray(32) { 7 } }
            val id = Uuid.random()
            home.putFromAnotherDevice(JOURNAL_COLLECTION, id.toString(), journalRecord("Deleted later"))
            device.manager.downloadRemoteChanges()
            assertEquals(
                DiagnosticReason.KEY_RECOVERY_REQUIRED.name,
                device.database.rows
                    .single { it.entityType == SPACE_UNREADABLE }
                    .reason,
            )

            home.deleteFromAnotherDevice(JOURNAL_COLLECTION, id.toString())
            device.manager.downloadRemoteChanges()
            device.identityKey = TEST_IDENTITY_KEY
            device.now = later
            device.manager.downloadRemoteChanges()

            assertNull(device.journal(id))
            assertEquals(
                "APPLIED",
                device.database.rows
                    .single { it.entityType == SPACE_UNREADABLE }
                    .state,
            )
            assertEquals(0, device.inbox.count())
        }

    @Test
    fun `a record deleted while it waits to be applied is never applied`() =
        runTest {
            val home = FakeDataHome()
            val journals = FailingJournals()
            val device = SpaceDevice(home, journals = journals)
            val id = Uuid.random()
            home.putFromAnotherDevice(JOURNAL_COLLECTION, id.toString(), journalRecord("Deleted later"))
            journals.failNextCreate = true
            device.manager.downloadRemoteChanges()
            assertNull(device.journal(id))
            assertEquals("FAILED", device.stagedJournalRows().single().state)

            home.deleteFromAnotherDevice(JOURNAL_COLLECTION, id.toString())
            device.manager.downloadRemoteChanges()
            device.now = later
            device.manager.downloadRemoteChanges()

            assertNull(device.journal(id))
            assertTrue(device.stagedJournalRows().single().deleted)
            assertEquals(0, device.inbox.count())
        }

    @Test
    fun `listing everything again deletes what was removed while waiting or unreadable`() =
        runTest {
            val home = FakeDataHome()
            val journals = FailingJournals()
            val device = SpaceDevice(home, journals = journals)
            val waiting = Uuid.random()
            val kept = Uuid.random()
            home.putFromAnotherDevice(JOURNAL_COLLECTION, kept.toString(), journalRecord("Kept"))
            home.putFromAnotherDevice(JOURNAL_COLLECTION, waiting.toString(), journalRecord("Removed while waiting"))
            journals.failCreateOf = waiting
            device.manager.downloadRemoteChanges()
            val keys = testKeys()

            home.deleteAndPurge(SealedRecordCodec.sealedRkey(keys, JOURNAL_COLLECTION, waiting.toString()))
            home.putFromAnotherDevice(JOURNAL_COLLECTION, kept.toString(), journalRecord("Kept and renamed", "2026-10-19T10:00:00Z"))
            journals.failCreateOf = null
            device.now = later
            val result = device.manager.downloadRemoteChanges()

            assertTrue(result.success, result.errors.toString())
            assertNull(device.journal(waiting))
            assertEquals("Kept and renamed", device.journal(kept)?.title)
            assertEquals(0, device.inbox.count())
        }

    @Test
    fun `listing everything again settles a record that was unreadable and is now gone`() =
        runTest {
            val home = FakeDataHome()
            val device = SpaceDevice(home).apply { identityKey = ByteArray(32) { 7 } }
            val id = Uuid.random()
            home.putFromAnotherDevice(JOURNAL_COLLECTION, id.toString(), journalRecord("Unreadable then removed"))
            device.manager.downloadRemoteChanges()

            home.deleteAndPurge(SealedRecordCodec.sealedRkey(testKeys(), JOURNAL_COLLECTION, id.toString()))
            home.putFromAnotherDevice(JOURNAL_COLLECTION, Uuid.random().toString(), journalRecord("Something new"))
            device.identityKey = TEST_IDENTITY_KEY
            device.now = later
            device.manager.downloadRemoteChanges()

            assertNull(device.journal(id))
            assertEquals(
                "APPLIED",
                device.database.rows
                    .single { it.entityType == SPACE_UNREADABLE }
                    .state,
            )
        }

    @Test
    fun `deleting here keeps an edit made elsewhere that this device hadn't seen and lists the conflict`() =
        runTest {
            val home = FakeDataHome()
            val device = SpaceDevice(home)
            val journal =
                Journal(
                    title = "Lake trips",
                    created = Instant.parse("2026-10-01T09:00:00Z"),
                    lastUpdated = Instant.parse("2026-10-01T09:00:00Z"),
                )
            device.create(journal)
            device.manager.uploadPendingChanges()
            home.putFromAnotherDevice(
                JOURNAL_COLLECTION,
                journal.id.toString(),
                journalRecord("Lake and river trips", "2026-10-02T09:00:00Z"),
            )

            device.delete(journal.id)
            val result = device.manager.uploadPendingChanges()

            assertEquals(SyncErrorType.CONFLICT_ERROR, result.errors.single().type)
            assertEquals("Lake and river trips", device.journal(journal.id)?.title)
            assertNotNull(home.open(JOURNAL_COLLECTION, journal.id.toString()))
            assertTrue(device.pendingJournals().isEmpty())
            assertEquals(
                home.stored(SealedRecordCodec.sealedRkey(testKeys(), JOURNAL_COLLECTION, journal.id.toString()))?.cid,
                device.swapStore.get(device.scope, JOURNAL_COLLECTION, journal.id.toString())?.cid,
            )
        }

    @Test
    fun `a resolver can let the deletion remove the edit too`() =
        runTest {
            val home = FakeDataHome()
            val deleting =
                object : ConflictResolver<Journal> by LastWriteWinsResolver() {
                    override fun resolve(
                        local: Journal,
                        remote: Journal,
                        localTimestamp: Instant,
                        remoteTimestamp: Instant,
                    ): ConflictResolution<Journal> = ConflictResolution.KeepLocal(local)

                    override fun resolveDeletion(
                        remote: Journal,
                        remoteTimestamp: Instant,
                    ) = DeletionResolution.DELETE
                }
            val device = SpaceDevice(home, resolver = deleting)
            val journal =
                Journal(
                    title = "Lake trips",
                    created = Instant.parse("2026-10-01T09:00:00Z"),
                    lastUpdated = Instant.parse("2026-10-01T09:00:00Z"),
                )
            device.create(journal)
            device.manager.uploadPendingChanges()
            home.putFromAnotherDevice(
                JOURNAL_COLLECTION,
                journal.id.toString(),
                journalRecord("Lake and river trips", "2026-10-02T09:00:00Z"),
            )

            device.delete(journal.id)
            val result = device.manager.uploadPendingChanges()

            assertTrue(result.success, result.errors.toString())
            assertNull(device.journal(journal.id))
            assertNull(home.open(JOURNAL_COLLECTION, journal.id.toString()))
        }

    @Test
    fun `a record still unreadable isn't opened again until this device holds another key`() =
        runTest {
            val home = FakeDataHome()
            val device = SpaceDevice(home).apply { identityKey = ByteArray(32) { 7 } }
            home.putFromAnotherDevice(JOURNAL_COLLECTION, Uuid.random().toString(), journalRecord("Unreadable"))
            device.manager.downloadRemoteChanges()

            fun attempts() =
                device.database.rows
                    .single { it.entityType == SPACE_UNREADABLE }
                    .attempts

            device.now = later
            device.manager.downloadRemoteChanges()
            assertEquals(1, attempts(), "Nothing changed, so it wasn't tried again")

            device.identityKey = ByteArray(32) { 9 }
            device.now = 2 * later
            device.manager.downloadRemoteChanges()
            assertEquals(2, attempts(), "Another key was worth trying")
        }

    @Test
    fun `one download pass reads the change feed once for every kind`() =
        runTest {
            val home = FakeDataHome()
            val device = SpaceDevice(home)
            home.putFromAnotherDevice(JOURNAL_COLLECTION, Uuid.random().toString(), journalRecord("From the tablet"))

            device.manager.downloadRemoteChanges()

            assertEquals(1, home.listChangesCalls)
            assertFalse(device.database.rows.isEmpty())
        }

    /** Journals whose next create, or every create of one journal, fails like a full disk would. */
    private class FailingJournals(
        private val delegate: FakeJournalRepository = FakeJournalRepository(),
    ) : JournalRepository by delegate {
        var failNextCreate = false
        var failCreateOf: Uuid? = null

        override suspend fun create(journal: Journal): Uuid {
            if (failNextCreate || journal.id == failCreateOf) {
                failNextCreate = false
                error("The disk is full")
            }
            return delegate.create(journal)
        }
    }
}
