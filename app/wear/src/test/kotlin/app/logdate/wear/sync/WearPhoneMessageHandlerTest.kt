package app.logdate.wear.sync

import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.datalayer.NoteAckPayload
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import app.logdate.client.sync.datalayer.v2.WearPaths
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/**
 * Tests [WearPhoneMessageHandler], which reacts to messages from the phone: acknowledgements that
 * a note is stored, requests to sync now, and the phone becoming reachable again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WearPhoneMessageHandlerTest {
    private val ackHandler = mockk<WearNoteAckHandler>(relaxed = true)
    private val syncManager = mockk<SyncManager>(relaxed = true)
    private val handler = WearPhoneMessageHandler(ackHandler, syncManager)

    @Test
    fun `an ack names the version of the note the phone stored`() =
        runTest {
            val noteId = Uuid.random()

            handler.onMessage(WearAudioRequestPaths.noteAckPath(noteId), NoteAckPayload(1_710_000_000_123).encode())

            coVerify(exactly = 1) { ackHandler.onNoteAcknowledged(noteId, NoteAckPayload(1_710_000_000_123)) }
            confirmVerified(syncManager)
        }

    @Test
    fun `an ack from an older phone has no version`() =
        runTest {
            val noteId = Uuid.random()

            handler.onMessage(WearAudioRequestPaths.noteAckPath(noteId))

            coVerify(exactly = 1) { ackHandler.onNoteAcknowledged(noteId, null) }
        }

    @Test
    fun `an ack payload this build cannot read is handled like an older phone's`() =
        runTest {
            val noteId = Uuid.random()

            handler.onMessage(WearAudioRequestPaths.noteAckPath(noteId), "garbage".encodeToByteArray())

            coVerify(exactly = 1) { ackHandler.onNoteAcknowledged(noteId, null) }
        }

    @Test
    fun `ack message with an invalid note id is ignored`() =
        runTest {
            handler.onMessage("/logdate/notes/not-a-uuid/ack")

            confirmVerified(ackHandler)
        }

    @Test
    fun `ack message that fails to record does not propagate`() =
        runTest {
            val noteId = Uuid.random()
            coEvery { ackHandler.onNoteAcknowledged(noteId, any()) } throws IllegalStateException("database closed")

            handler.onMessage(WearAudioRequestPaths.noteAckPath(noteId))

            coVerify(exactly = 1) { ackHandler.onNoteAcknowledged(noteId, any()) }
        }

    @Test
    fun `sync request message sends pending changes`() =
        runTest {
            handler.onMessage(WearAudioRequestPaths.SYNC_REQUEST_PATH)

            coVerify(exactly = 1) { syncManager.fullSync() }
            confirmVerified(ackHandler)
        }

    @Test
    fun `unknown message path does nothing`() =
        runTest {
            handler.onMessage("/logdate/other/thing")

            confirmVerified(ackHandler, syncManager)
        }

    @Test
    fun `phone becoming reachable sends pending changes`() =
        runTest {
            handler.onPhoneReachable()

            coVerify(exactly = 1) { syncManager.fullSync() }
        }

    @Test
    fun `a failed sync does not propagate`() =
        runTest {
            coEvery { syncManager.fullSync() } throws IllegalStateException("no connection")

            handler.onPhoneReachable()

            coVerify(exactly = 1) { syncManager.fullSync() }
        }

    @Test
    fun `a receipt push forwards its source and wakes sync after delivery advances`() =
        runTest {
            val received = mutableListOf<Pair<String?, List<Byte>>>()
            val scoped =
                WearPhoneMessageHandler(ackHandler, syncManager, { source, bytes ->
                    received += source to bytes.toList()
                    1
                })
            scoped.onMessage(WearPaths.RECEIPTS_PUSH, byteArrayOf(1, 2), "phone")
            assertEquals(listOf<Pair<String?, List<Byte>>>("phone" to listOf<Byte>(1, 2)), received)
            coVerify(exactly = 1) { syncManager.fullSync() }
            confirmVerified(ackHandler)
        }

    @Test
    fun `a receipt that changes nothing does not wake sync`() =
        runTest {
            val scoped = WearPhoneMessageHandler(ackHandler, syncManager, { _, _ -> 0 })
            scoped.onMessage(WearPaths.RECEIPTS_PUSH, byteArrayOf(1), "phone")
            confirmVerified(ackHandler, syncManager)
        }

    @Test
    fun `cancellation while applying a receipt propagates`() =
        runTest {
            val scoped = WearPhoneMessageHandler(ackHandler, syncManager, { _, _ -> throw CancellationException("stopped") })
            assertFailsWith<CancellationException> { scoped.onMessage(WearPaths.RECEIPTS_PUSH, byteArrayOf(1), "phone") }
            confirmVerified(ackHandler, syncManager)
        }

    @Test
    fun `legacy acknowledgements cannot settle notes while receipts own delivery`() =
        runTest {
            val scoped = WearPhoneMessageHandler(ackHandler, syncManager, legacyAcksAllowed = { false })
            scoped.onMessage(WearAudioRequestPaths.noteAckPath(Uuid.random()))
            confirmVerified(ackHandler, syncManager)
        }

    @Test
    fun `cancellation while syncing propagates`() =
        runTest {
            coEvery { syncManager.fullSync() } throws CancellationException("stopped")
            assertFailsWith<CancellationException> { handler.onPhoneReachable() }
        }

    @Test
    fun `cancellation while storing a legacy ack propagates`() =
        runTest {
            coEvery { ackHandler.onNoteAcknowledged(any(), any()) } throws CancellationException("stopped")
            assertFailsWith<CancellationException> { handler.onMessage(WearAudioRequestPaths.noteAckPath(Uuid.random())) }
        }
}
