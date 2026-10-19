package app.logdate.client.sync.space

import app.logdate.shared.model.Journal
import app.logdate.shared.model.diagnostics.DiagnosticReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SpaceDownloadTest {
    @Test
    fun `a journal written on another device is applied and its version remembered`() =
        runTest {
            val device = SpaceDevice(FakeDataHome())
            val id = Uuid.random()
            val stored = device.home.putFromAnotherDevice(JOURNAL_COLLECTION, id.toString(), journalRecord("From the tablet"))

            val result = device.manager.downloadRemoteChanges()

            assertTrue(result.success, result.errors.toString())
            val journal = assertNotNull(device.journal(id))
            assertEquals("From the tablet", journal.title)
            assertEquals(stored.seq, journal.syncVersion)
            assertEquals(stored.cid, device.swapStore.get(device.scope, JOURNAL_COLLECTION, id.toString())?.cid)
            assertEquals(stored.seq, device.inbox.cursor(SPACE_CHECKPOINT))
            assertEquals(0, device.inbox.count())
        }

    @Test
    fun `this device's own write isn't applied again`() =
        runTest {
            val device = SpaceDevice(FakeDataHome())
            val journal = Journal(title = "Weekend walks", created = Instant.parse("2026-10-01T09:00:00Z"))
            device.create(journal)
            device.manager.uploadPendingChanges()
            val uploaded = device.journal(journal.id)

            device.manager.downloadRemoteChanges()

            assertEquals(uploaded, device.journal(journal.id))
            assertTrue(device.stagedJournalRows().isEmpty())
        }

    @Test
    fun `a deletion on another device removes the local journal`() =
        runTest {
            val device = SpaceDevice(FakeDataHome())
            val id = Uuid.random()
            device.home.putFromAnotherDevice(JOURNAL_COLLECTION, id.toString(), journalRecord("From the tablet"))
            device.manager.downloadRemoteChanges()

            device.home.deleteFromAnotherDevice(JOURNAL_COLLECTION, id.toString())
            val result = device.manager.downloadRemoteChanges()

            assertTrue(result.success, result.errors.toString())
            assertNull(device.journal(id))
            assertNull(device.swapStore.get(device.scope, JOURNAL_COLLECTION, id.toString()))
        }

    @Test
    fun `a record sealed with a key this device lacks is kept until the key is recovered`() =
        runTest {
            val device = SpaceDevice(FakeDataHome()).apply { identityKey = ByteArray(32) { 7 } }
            val id = Uuid.random()
            device.home.putFromAnotherDevice(JOURNAL_COLLECTION, id.toString(), journalRecord("From the tablet"))

            device.manager.downloadRemoteChanges()

            assertNull(device.journal(id))
            val kept = device.database.rows.single { it.entityType == SPACE_UNREADABLE }
            assertEquals(DiagnosticReason.KEY_RECOVERY_REQUIRED.name, kept.reason)
            assertEquals(1, device.inbox.count(), "An unreadable record still counts as waiting")

            device.identityKey = TEST_IDENTITY_KEY
            device.database.rows =
                device.database.rows
                    .map { it.copy(nextAttemptAt = 0) }
                    .toMutableList()
            device.manager.downloadRemoteChanges()

            assertEquals("From the tablet", device.journal(id)?.title)
            assertEquals(0, device.inbox.count())
        }

    @Test
    fun `a record that fails its schema is reported as corrupt`() =
        runTest {
            val device = SpaceDevice(FakeDataHome())
            val id = Uuid.random()
            val untitled =
                buildJsonObject {
                    put("\$type", JOURNAL_COLLECTION)
                    put("createdAt", "2026-10-01T09:00:00Z")
                }
            device.home.putFromAnotherDevice(JOURNAL_COLLECTION, id.toString(), untitled)

            device.manager.downloadRemoteChanges()

            assertNull(device.journal(id))
            assertEquals(
                DiagnosticReason.CORRUPT_PAYLOAD.name,
                device.database.rows
                    .single { it.entityType == SPACE_UNREADABLE }
                    .reason,
            )
        }

    @Test
    fun `a record type this build doesn't sync is kept for a newer version`() =
        runTest {
            val device = SpaceDevice(FakeDataHome())
            device.home.putFromAnotherDevice(
                "app.logdate.journal.draft",
                "draft1",
                buildJsonObject { put("\$type", "app.logdate.journal.draft") },
            )

            val result = device.manager.downloadRemoteChanges()

            assertTrue(result.success, result.errors.toString())
            val kept = device.database.rows.single { it.entityType == SPACE_UNREADABLE }
            assertEquals(DiagnosticReason.UNSUPPORTED_FORMAT.name, kept.reason)
            assertEquals(0, device.inbox.count(), "Only an app update reads it, so nothing is left for this version to finish")
        }

    @Test
    fun `a cursor from before purged deletions lists everything again and removes what's gone`() =
        runTest {
            val home = FakeDataHome()
            val device = SpaceDevice(home)
            val kept = Uuid.random()
            val removed = Uuid.random()
            home.putFromAnotherDevice(JOURNAL_COLLECTION, kept.toString(), journalRecord("Kept"))
            home.putFromAnotherDevice(JOURNAL_COLLECTION, removed.toString(), journalRecord("Removed"))
            device.manager.downloadRemoteChanges()
            assertNotNull(device.journal(removed))

            home.deleteAndPurge(
                app.logdate.space.SealedRecordCodec
                    .sealedRkey(testKeys(), JOURNAL_COLLECTION, removed.toString()),
            )
            home.putFromAnotherDevice(JOURNAL_COLLECTION, kept.toString(), journalRecord("Kept and renamed", "2026-10-19T10:00:00Z"))
            val result = device.manager.downloadRemoteChanges()

            assertTrue(result.success, result.errors.toString())
            assertNull(device.journal(removed))
            assertEquals("Kept and renamed", device.journal(kept)?.title)
            assertFalse(
                device.swapStore.states.values
                    .any { it.rkey == removed.toString() },
            )
        }

    @Test
    fun `while the gate is closed the space isn't read`() =
        runTest {
            val device = SpaceDevice(FakeDataHome()).apply { gateOpen = false }
            device.home.putFromAnotherDevice(JOURNAL_COLLECTION, Uuid.random().toString(), journalRecord("From the tablet"))

            device.manager.downloadRemoteChanges()

            assertTrue(device.home.tokensSeen.isEmpty())
            assertTrue(
                device.journals.allJournalsObserved
                    .first()
                    .isEmpty(),
            )
        }
}
