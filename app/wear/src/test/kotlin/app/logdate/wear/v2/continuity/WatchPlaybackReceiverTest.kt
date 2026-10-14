package app.logdate.wear.v2.continuity

import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.continuity.PlaybackHandoffResult
import app.logdate.client.sync.datalayer.v2.Decoded
import app.logdate.client.sync.datalayer.v2.LinkMode
import app.logdate.client.sync.datalayer.v2.LivePlaybackCommand
import app.logdate.client.sync.datalayer.v2.PeerDeliveryContext
import app.logdate.client.sync.datalayer.v2.PlaybackCommandType
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.datalayer.v2.WearProtocol
import app.logdate.wear.sync.v2.delivery.WatchDeliveryPeer
import app.logdate.wear.v2.playback.MomentPlayer
import app.logdate.wear.v2.playback.voiceNote
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WatchPlaybackReceiverTest {
    @Test
    fun `cold media startup can exceed four seconds without abandoning handoff`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                val note = voiceNote()
                val context = PeerDeliveryContext("link", "phone", "watch", LinkMode.PAIR)
                val peer = mockk<WatchDeliveryPeer>()
                val notes = mockk<JournalNotesRepository>()
                val player = mockk<MomentPlayer>()
                coEvery { peer.current() } returns context
                coEvery { peer.isCurrent(context) } returns true
                coEvery { notes.getNoteById(note.uid) } returns note
                coEvery { player.continuePlayback(note, 1_000L, 1f) } coAnswers {
                    delay(6_000)
                    true
                }
                val command =
                    LivePlaybackCommand(
                        sessionId = "session",
                        noteId = note.uid.toString(),
                        command = PlaybackCommandType.CONTINUE_ON_WATCH,
                        positionMs = 1_000L,
                        speed = 1f,
                    )
                val result =
                    WatchPlaybackReceiver(peer, notes, player).answer(
                        "phone",
                        WearJson.encodeToBytes(LivePlaybackCommand.serializer(), command),
                    )
                val decoded = WearJson.decode(result, WearProtocol.LIVE_SCHEMA, PlaybackHandoffResult.serializer()) as Decoded.Supported
                assertTrue(decoded.value.accepted)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `note arriving after window publication can be handed off`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                val note = voiceNote()
                val context = PeerDeliveryContext("link", "phone", "watch", LinkMode.PAIR)
                val peer = mockk<WatchDeliveryPeer>()
                val notes = mockk<JournalNotesRepository>()
                val player = mockk<MomentPlayer>()
                coEvery { peer.current() } returns context
                coEvery { peer.isCurrent(context) } returns true
                var calls = 0
                coEvery { notes.getNoteById(note.uid) } answers { if (++calls >= 3) note else null }
                coEvery { player.continuePlayback(note, 0L, 1f) } returns true
                val command =
                    LivePlaybackCommand(
                        sessionId = "session",
                        noteId = note.uid.toString(),
                        command = PlaybackCommandType.CONTINUE_ON_WATCH,
                    )
                val result =
                    WatchPlaybackReceiver(peer, notes, player).answer(
                        "phone",
                        WearJson.encodeToBytes(LivePlaybackCommand.serializer(), command),
                    )
                val decoded = WearJson.decode(result, WearProtocol.LIVE_SCHEMA, PlaybackHandoffResult.serializer()) as Decoded.Supported
                assertTrue(decoded.value.accepted)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `revocation during playback start stops the watch before rejecting handoff`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                val note = voiceNote()
                val context = PeerDeliveryContext("link", "phone", "watch", LinkMode.PAIR)
                val peer = mockk<WatchDeliveryPeer>()
                val notes = mockk<JournalNotesRepository>()
                val player = mockk<MomentPlayer>()
                coEvery { peer.current() } returns context
                coEvery { peer.isCurrent(context) } returns false
                coEvery { notes.getNoteById(note.uid) } returns note
                coEvery { player.continuePlayback(note, 1_000L, 1f) } returns true
                every { player.stopIfPlaying(note.uid) } just Runs
                val command =
                    LivePlaybackCommand(
                        sessionId = "session",
                        noteId = note.uid.toString(),
                        command = PlaybackCommandType.CONTINUE_ON_WATCH,
                        positionMs = 1_000L,
                        speed = 1f,
                    )

                val result =
                    WatchPlaybackReceiver(peer, notes, player).answer(
                        "phone",
                        WearJson.encodeToBytes(LivePlaybackCommand.serializer(), command),
                    )

                val decoded = WearJson.decode(result, WearProtocol.LIVE_SCHEMA, PlaybackHandoffResult.serializer()) as Decoded.Supported
                assertFalse(decoded.value.accepted)
                verify(exactly = 1) { player.stopIfPlaying(note.uid) }
            } finally {
                Dispatchers.resetMain()
            }
        }
}
