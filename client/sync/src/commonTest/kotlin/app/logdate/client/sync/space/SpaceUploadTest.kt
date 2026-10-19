package app.logdate.client.sync.space

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.SyncErrorType
import app.logdate.client.sync.metadata.EntityType
import app.logdate.shared.model.Journal
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import studio.hypertext.atproto.xrpc.XrpcProtocolException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SpaceUploadTest {
    private val created = Instant.parse("2026-10-01T09:00:00Z")

    @Test
    fun `a new journal is sealed into the space only if absent and its outbox entry settles`() =
        runTest {
            val device = SpaceDevice(FakeDataHome())
            val journal = Journal(title = "Weekend walks", created = created, lastUpdated = created)
            device.create(journal)

            val result = device.manager.uploadPendingChanges()

            assertTrue(result.success, result.errors.toString())
            assertEquals(1, result.uploadedItems)
            val opened = device.home.openJournal(journal.id.toString())
            assertEquals(JOURNAL_COLLECTION, opened.collection)
            assertEquals(TestJournalMapper.toRecord(journal), opened.record.jsonObject)
            val state = assertNotNull(device.swapStore.get(device.scope, JOURNAL_COLLECTION, journal.id.toString()))
            assertEquals(state.seq, device.journal(journal.id)?.syncVersion)
            assertTrue(device.pendingJournals().isEmpty())
        }

    @Test
    fun `an edit replaces only the version this device last saw`() =
        runTest {
            val device = SpaceDevice(FakeDataHome())
            val journal = Journal(title = "Weekend walks", created = created, lastUpdated = created)
            device.create(journal)
            device.manager.uploadPendingChanges()

            device.edit(device.journal(journal.id)!!.copy(title = "Weekday walks", lastUpdated = created + 1.hours))
            val result = device.manager.uploadPendingChanges()

            assertTrue(result.success, result.errors.toString())
            assertEquals(2, device.home.writes)
            assertEquals(
                "Weekday walks",
                device.home
                    .openJournal(journal.id.toString())
                    .record.jsonObject["title"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(2L, device.swapStore.get(device.scope, JOURNAL_COLLECTION, journal.id.toString())?.seq)
        }

    @Test
    fun `an older edit that lost a race takes the newer version from the space`() =
        runTest {
            val device = SpaceDevice(FakeDataHome())
            val journal = Journal(title = "Weekend walks", created = created, lastUpdated = created)
            device.create(journal)
            device.manager.uploadPendingChanges()
            device.home.putFromAnotherDevice(
                JOURNAL_COLLECTION,
                journal.id.toString(),
                journalRecord("Edited on the tablet", "2026-10-03T12:00:00Z"),
            )

            device.edit(
                device.journal(journal.id)!!.copy(title = "Edited on the phone", lastUpdated = Instant.parse("2026-10-02T12:00:00Z")),
            )
            val result = device.manager.uploadPendingChanges()

            assertTrue(result.success, result.errors.toString())
            assertEquals("Edited on the tablet", device.journal(journal.id)?.title)
            assertEquals(2, device.home.writes, "Keeping the other device's version writes nothing")
            assertTrue(device.pendingJournals().isEmpty())
            assertEquals(
                device.home.stored(sealedJournalKey(journal))?.cid,
                device.swapStore.get(device.scope, JOURNAL_COLLECTION, journal.id.toString())?.cid,
            )
        }

    @Test
    fun `a newer edit that lost a race is written over the version it just read`() =
        runTest {
            val device = SpaceDevice(FakeDataHome())
            val journal = Journal(title = "Weekend walks", created = created, lastUpdated = created)
            device.create(journal)
            device.manager.uploadPendingChanges()
            device.home.putFromAnotherDevice(
                JOURNAL_COLLECTION,
                journal.id.toString(),
                journalRecord("Edited on the tablet", "2026-10-02T12:00:00Z"),
            )

            device.edit(
                device.journal(journal.id)!!.copy(title = "Edited on the phone", lastUpdated = Instant.parse("2026-10-03T12:00:00Z")),
            )
            val result = device.manager.uploadPendingChanges()

            assertTrue(result.success, result.errors.toString())
            assertEquals(
                "Edited on the phone",
                device.home
                    .openJournal(journal.id.toString())
                    .record.jsonObject["title"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(3, device.home.writes)
            assertTrue(device.pendingJournals().isEmpty())
        }

    @Test
    fun `a deletion removes the record and forgets its version`() =
        runTest {
            val device = SpaceDevice(FakeDataHome())
            val journal = Journal(title = "Weekend walks", created = created, lastUpdated = created)
            device.create(journal)
            device.manager.uploadPendingChanges()

            device.delete(journal.id)
            val result = device.manager.uploadPendingChanges()

            assertTrue(result.success, result.errors.toString())
            assertNull(device.home.stored(sealedJournalKey(journal)))
            assertNull(device.swapStore.get(device.scope, JOURNAL_COLLECTION, journal.id.toString()))
            assertTrue(device.pendingJournals().isEmpty())
        }

    @Test
    fun `a record its schema rejects is never sent and is set aside`() =
        runTest {
            val device = SpaceDevice(FakeDataHome())
            device.create(Journal(title = "x".repeat(201), created = created, lastUpdated = created))

            val result = device.manager.uploadPendingChanges()

            assertFalse(result.success)
            assertEquals(0, device.home.writes)
            assertTrue(
                result.errors
                    .single()
                    .message
                    .contains("title"),
                result.errors.toString(),
            )
            assertFalse(result.errors.single().retryable)
            assertEquals(1, device.deadLetters.list().size)
        }

    @Test
    fun `an expired token is refreshed and the write sent again`() =
        runTest {
            val home = FakeDataHome().apply { acceptedTokens.remove("test-access-token") }
            val device = SpaceDevice(home)
            device.create(Journal(title = "Weekend walks", created = created, lastUpdated = created))

            val result = device.manager.uploadPendingChanges()

            assertTrue(result.success, result.errors.toString())
            assertEquals(listOf("test-access-token", "new-token"), home.tokensSeen)
            assertEquals("new-token", device.sessions.getSession()?.accessToken)
        }

    @Test
    fun `a rate limit stops the whole pass and the next one until Retry-After`() =
        runTest {
            val home =
                FakeDataHome().apply {
                    failures +=
                        XrpcProtocolException(429, "RateLimitExceeded", "Slow down", retryAfterSeconds = 600)
                }
            val device = SpaceDevice(home)
            val journal = Journal(title = "Weekend walks", created = created, lastUpdated = created)
            device.create(journal)
            device.create(Journal(title = "Evening runs", created = created, lastUpdated = created))
            device.saveNote(JournalNote.Text(creationTimestamp = created, lastUpdated = created, content = "Swam"))

            val result = device.manager.uploadPendingChanges()

            assertFalse(result.success)
            assertTrue(result.errors.all { it.retryable }, result.errors.toString())
            assertEquals(1, home.tokensSeen.size, "Only the refused request was sent")
            val next = assertNotNull(device.retrySchedule.nextAttemptAt(EntityType.JOURNAL, journal.id.toString()))
            assertTrue(next >= (Clock.System.now() + 590.seconds).toEpochMilliseconds(), "Retried before the server's Retry-After")
            assertEquals(2, device.pendingJournals().size)
            assertEquals(1, device.pendingNotes().size)

            device.manager.uploadPendingChanges()
            device.manager.downloadRemoteChanges()

            assertEquals(1, home.tokensSeen.size, "Nothing is sent while the limit applies")
        }

    @Test
    fun `a full quota is reported as full storage`() =
        runTest {
            val home = FakeDataHome().apply { failures += XrpcProtocolException(507, "QuotaExceeded", "No room") }
            val device = SpaceDevice(home)
            device.create(Journal(title = "Weekend walks", created = created, lastUpdated = created))

            val result = device.manager.uploadPendingChanges()

            assertEquals(SyncErrorType.STORAGE_ERROR, result.errors.single().type)
        }

    @Test
    fun `while the gate is closed nothing goes to the space`() =
        runTest {
            val device = SpaceDevice(FakeDataHome()).apply { gateOpen = false }
            device.create(Journal(title = "Weekend walks", created = created, lastUpdated = created))

            device.manager.uploadPendingChanges()

            assertTrue(device.home.tokensSeen.isEmpty())
            assertTrue(device.home.liveRkeys().isEmpty())
        }

    @Test
    fun `an account without a DID gets a visible error rather than a silent skip`() =
        runTest {
            val device = SpaceDevice(FakeDataHome(), did = null)
            device.create(Journal(title = "Weekend walks", created = created, lastUpdated = created))

            val result = device.manager.uploadPendingChanges()

            assertFalse(result.success)
            assertTrue(result.errors.isNotEmpty() && result.errors.all { "DID" in it.message }, result.errors.toString())
            assertEquals(1, device.pendingJournals().size)
        }

    private suspend fun sealedJournalKey(journal: Journal): String =
        app.logdate.space.SealedRecordCodec
            .sealedRkey(testKeys(), JOURNAL_COLLECTION, journal.id.toString())
}
