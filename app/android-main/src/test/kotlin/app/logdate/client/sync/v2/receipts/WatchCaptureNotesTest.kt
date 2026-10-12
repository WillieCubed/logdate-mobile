package app.logdate.client.sync.v2.receipts

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class WatchCaptureNotesTest {
    private val delegate = mockk<SyncableJournalNotesRepository>()
    private val outbox = mockk<SyncMetadataService>()
    private var remembered = false
    private var backupRequests = 0
    private var current = true
    private var insideTransaction = false
    private var rolledBack = false
    private var stored: JournalNote? = null
    private var enqueued: PendingOperation? = null
    private val transaction =
        object : SyncTransactionManager {
            override suspend fun <T> withTransaction(block: suspend () -> T): T {
                val beforeOrigin = remembered
                val before = stored
                val beforePending = enqueued
                insideTransaction = true
                try {
                    return block()
                } catch (e: Throwable) {
                    remembered = beforeOrigin
                    stored = before
                    enqueued = beforePending
                    rolledBack = true
                    throw e
                } finally {
                    insideTransaction = false
                }
            }
        }
    private val notes =
        WatchCaptureNotes(delegate, transaction, outbox, { current }, {
            assertFalse(insideTransaction)
            backupRequests++
        }, {
            assertTrue(insideTransaction)
            remembered = true
        })
    private val note =
        JournalNote.Text(
            uid = Uuid.random(),
            content = "watch capture",
            creationTimestamp = Instant.fromEpochMilliseconds(1),
            lastUpdated = Instant.fromEpochMilliseconds(5),
        )

    init {
        coEvery { delegate.getNoteById(note.uid) } answers { stored }
        coEvery { delegate.create(any()) } coAnswers {
            assertTrue(insideTransaction)
            stored = firstArg()
            stored!!.uid
        }
        coEvery { delegate.createFromSync(any()) } coAnswers {
            assertTrue(insideTransaction)
            if (stored == null) stored = firstArg()
        }
        coEvery { delegate.deleteFromSync(note.uid) } coAnswers {
            assertTrue(insideTransaction)
            stored = null
        }
        coEvery { outbox.hasPending(EntityType.NOTE, note.uid.toString()) } answers { enqueued != null }
        coEvery { outbox.enqueuePending(note.uid.toString(), EntityType.NOTE, any()) } coAnswers {
            assertTrue(insideTransaction)
            enqueued = thirdArg()
        }
    }

    @Test
    fun `a durable removal barrier prevents a late capture from being created`() =
        runTest {
            val guarded = WatchCaptureNotes(delegate, transaction, outbox, { true }, { }, { }, canStore = { false })
            guarded.createFromSync(note)
            assertNull(stored)
            assertNull(enqueued)
            assertFalse(remembered)
        }

    @Test
    fun `a current capture is created within the guarded transaction`() =
        runTest {
            assertEquals(note.uid, notes.create(note))
            assertEquals(note, stored)
            assertFalse(rolledBack)
        }

    @Test
    fun `a stale context cannot begin a note write`() =
        runTest {
            current = false
            assertFailsWith<StaleWatchCapture> { notes.create(note) }
            coVerify(exactly = 0) { delegate.create(any()) }
            assertEquals(null, stored)
        }

    @Test
    fun `a context change during storage rolls back the note`() =
        runTest {
            coEvery { delegate.create(any()) } coAnswers {
                stored = firstArg()
                current = false
                note.uid
            }
            assertFailsWith<StaleWatchCapture> { notes.create(note) }
            assertTrue(rolledBack)
            assertEquals(null, stored)
        }

    @Test
    fun `an old delivery cannot overwrite a newer phone edit`() =
        runTest {
            val newer = note.copy(content = "newer phone edit", lastUpdated = Instant.fromEpochMilliseconds(9), syncVersion = 7)
            stored = newer
            notes.createFromSync(note)
            assertEquals(newer, stored)
            coVerify(exactly = 0) { delegate.createFromSync(any()) }
        }

    @Test
    fun `a same version redelivery preserves phone content`() =
        runTest {
            val phone = note.copy(content = "phone content", syncVersion = 7)
            stored = phone
            notes.createFromSync(note)
            assertEquals(phone, stored)
            coVerify(exactly = 0) { delegate.createFromSync(any()) }
        }

    @Test
    fun `a watch update waits while a phone edit is pending`() =
        runTest {
            val phone = note.copy(content = "pending phone edit", lastUpdated = Instant.fromEpochMilliseconds(3), syncVersion = 7)
            stored = phone
            enqueued = PendingOperation.UPDATE
            notes.createFromSync(note)
            assertEquals(phone, stored)
            assertEquals(PendingOperation.UPDATE, enqueued)
            coVerify(exactly = 0) { delegate.createFromSync(any()) }
        }

    @Test
    fun `a newer watch edit preserves the phone server version and enters its outbox`() =
        runTest {
            stored = note.copy(content = "earlier capture", lastUpdated = Instant.fromEpochMilliseconds(3), syncVersion = 7)
            notes.createFromSync(note)
            assertEquals(note.copy(syncVersion = 7), stored)
            assertEquals(PendingOperation.UPDATE, enqueued)
        }

    @Test
    fun `a context change during update queueing rolls back both writes`() =
        runTest {
            val earlier = note.copy(content = "earlier", lastUpdated = Instant.fromEpochMilliseconds(3), syncVersion = 7)
            stored = earlier
            coEvery { outbox.enqueuePending(note.uid.toString(), EntityType.NOTE, any()) } coAnswers {
                enqueued = thirdArg()
                current = false
            }
            assertFailsWith<StaleWatchCapture> { notes.createFromSync(note) }
            assertTrue(rolledBack)
            assertEquals(earlier, stored)
            assertEquals(null, enqueued)
        }

    @Test
    fun `cancellation during storage is propagated and rolled back`() =
        runTest {
            coEvery { delegate.create(any()) } coAnswers {
                stored = firstArg()
                throw CancellationException("Stopped")
            }
            assertFailsWith<CancellationException> { notes.create(note) }
            assertTrue(rolledBack)
            assertEquals(null, stored)
        }

    @Test
    fun `a stale reader cannot expose the current phone note`() =
        runTest {
            stored = note
            current = false
            assertFailsWith<StaleWatchCapture> { notes.getNoteById(note.uid) }
        }

    @Test
    fun `a link change during a note lookup discards the result`() =
        runTest {
            coEvery { delegate.getNoteById(note.uid) } coAnswers {
                current = false
                note
            }
            assertFailsWith<StaleWatchCapture> { notes.getNoteById(note.uid) }
        }

    @Test
    fun `a link change during update lookup never starts storage`() =
        runTest {
            val earlier = note.copy(lastUpdated = Instant.fromEpochMilliseconds(3))
            stored = earlier
            coEvery { delegate.getNoteById(note.uid) } coAnswers {
                current = false
                earlier
            }
            assertFailsWith<StaleWatchCapture> { notes.createFromSync(note) }
            coVerify(exactly = 0) { delegate.createFromSync(any()) }
            assertEquals(earlier, stored)
        }

    @Test
    fun `an update arriving before the original capture is queued as a create`() =
        runTest {
            notes.createFromSync(note)
            assertEquals(note, stored)
            coVerify(exactly = 1) { delegate.create(note) }
            coVerify(exactly = 0) { delegate.createFromSync(any()) }
        }

    @Test
    fun `an accepted watch edit requests backup after committing`() =
        runTest {
            stored = note.copy(lastUpdated = Instant.fromEpochMilliseconds(3))
            notes.createFromSync(note)
            assertEquals(1, backupRequests)
        }

    @Test
    fun `a rolled back watch edit does not request backup`() =
        runTest {
            stored = note.copy(lastUpdated = Instant.fromEpochMilliseconds(3))
            coEvery { outbox.enqueuePending(note.uid.toString(), EntityType.NOTE, any()) } coAnswers { current = false }
            assertFailsWith<StaleWatchCapture> { notes.createFromSync(note) }
            assertEquals(0, backupRequests)
        }

    @Test
    fun `an ignored old watch edit does not request backup`() =
        runTest {
            stored = note
            notes.createFromSync(note)
            assertEquals(0, backupRequests)
        }

    @Test
    fun `capture origin is stored before create can start backup`() =
        runTest {
            coEvery { delegate.create(any()) } coAnswers {
                assertTrue(remembered)
                stored = firstArg()
                note.uid
            }
            notes.create(note)
            assertEquals(note, stored)
        }

    @Test
    fun `a failed create does not retain capture provenance`() =
        runTest {
            coEvery { delegate.create(any()) } coAnswers { throw IllegalStateException("disk full") }
            assertFailsWith<IllegalStateException> { notes.create(note) }
            assertFalse(remembered)
        }
}
