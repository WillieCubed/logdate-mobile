package app.logdate.client.media.audio

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.Futures
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidAudioPlaybackManagerTest {

    private val context = mockk<Context>()
    private val controller = mockk<MediaController>()
    private val controllerListener = slot<Player.Listener>()
    private val directExecutor = Executor { runnable -> runnable.run() }

    @Test
    fun `startPlayback starts service and configures media controller with metadata`() = runTest {
        val currentPosition = PlaybackStateHolder(250L)
        val noteId = Uuid.random()
        val itemFactory = RecordingAudioPlaybackItemFactory()
        val metadata =
            AudioPlaybackMetadata(
                title = "Voice Note",
                subtitle = "March 31",
                noteId = noteId,
            )
        val manager = createManager(this, currentPosition = currentPosition, itemFactory = itemFactory)

        every { controller.isPlaying } returns true

        manager.startPlayback(
            uri = "file:///tmp/voice-note.m4a",
            metadata = metadata,
            onProgressUpdated = {},
            onPlaybackCompleted = {},
        )

        verify(exactly = 1) { context.startService(any<Intent>()) }
        verify(exactly = 1) { controller.setMediaItem(itemFactory.mediaItem) }
        verify(exactly = 1) { controller.prepare() }
        verify(exactly = 1) { controller.play() }
        assertTrue(manager.playbackStatus.value.isPlaying)
        assertEquals(0.25f, manager.playbackStatus.value.progress)
        assertEquals("file:///tmp/voice-note.m4a", itemFactory.lastUri)
        assertEquals(metadata, itemFactory.lastMetadata)
        manager.release()
    }

    @Test
    fun `pause seek and stop delegate to controller and reset progress`() = runTest {
        val manager = createManager(this, currentPosition = PlaybackStateHolder(400L))

        manager.startPlayback(
            uri = "file:///tmp/voice-note.m4a",
            onProgressUpdated = {},
            onPlaybackCompleted = {},
        )
        manager.pausePlayback()
        manager.seekTo(0.5f)
        manager.stopPlayback()

        verify(exactly = 1) { controller.pause() }
        verify(exactly = 1) { controller.seekTo(500L) }
        verify(exactly = 1) { controller.stop() }
        assertEquals(0f, manager.playbackStatus.value.progress)
        manager.release()
    }

    @Test
    fun `playback ended listener reports completion and full progress`() = runTest {
        val progressUpdates = mutableListOf<Float>()
        var completedCount = 0
        val currentPosition = PlaybackStateHolder(0L)
        val manager = createManager(this, currentPosition = currentPosition)

        manager.startPlayback(
            uri = "file:///tmp/voice-note.m4a",
            onProgressUpdated = { progressUpdates += it },
            onPlaybackCompleted = { completedCount += 1 },
        )

        currentPosition.value = 1_000L
        controllerListener.captured.onPlaybackStateChanged(Player.STATE_ENDED)

        assertEquals(1f, progressUpdates.last())
        assertEquals(1, completedCount)
        assertEquals(1f, manager.playbackStatus.value.progress)
        assertFalse(manager.playbackStatus.value.isPlaying)
        manager.release()
    }

    @Test
    fun `unsuitable audio output suppression updates playback status`() = runTest {
        val manager = createManager(this, currentPosition = PlaybackStateHolder(100L))

        manager.startPlayback(
            uri = "file:///tmp/voice-note.m4a",
            onProgressUpdated = {},
            onPlaybackCompleted = {},
        )

        every {
            controller.playbackSuppressionReason
        } returns Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT

        controllerListener.captured.onPlaybackSuppressionReasonChanged(
            Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT,
        )

        assertTrue(manager.playbackStatus.value.isSuppressedForUnsuitableOutput)
        manager.release()
    }

    @Test
    fun `isPlaying listener starts progress tracking on injected dispatcher`() = runTest {
        val progressUpdates = mutableListOf<Float>()
        val currentPosition = PlaybackStateHolder(200L)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = createManager(this, dispatcher = dispatcher, currentPosition = currentPosition)

        every { controller.isPlaying } returns true

        manager.startPlayback(
            uri = "file:///tmp/voice-note.m4a",
            onProgressUpdated = { progressUpdates += it },
            onPlaybackCompleted = {},
        )

        controllerListener.captured.onIsPlayingChanged(true)
        runCurrent()
        currentPosition.value = 500L
        advanceTimeBy(100)
        runCurrent()

        assertEquals(0.5f, progressUpdates.last())
        assertEquals(0.5f, manager.playbackStatus.value.progress)
        controllerListener.captured.onIsPlayingChanged(false)
        manager.release()
    }

    @Test
    fun `release removes listener and releases controller`() = runTest {
        val manager = createManager(this, currentPosition = PlaybackStateHolder(0L))

        manager.startPlayback(
            uri = "file:///tmp/voice-note.m4a",
            onProgressUpdated = {},
            onPlaybackCompleted = {},
        )
        manager.release()

        verify(exactly = 1) { controller.removeListener(controllerListener.captured) }
        verify(exactly = 1) { controller.release() }
    }

    @Test
    fun `controller creation failure invokes completion callback`() = runTest {
        var completedCount = 0
        val manager =
            createManager(
                scope = this,
                currentPosition = PlaybackStateHolder(0L),
                controllerFutureFactory =
                    MediaControllerFutureFactory {
                        Futures.immediateFailedFuture(IllegalStateException("boom"))
                    },
            )

        manager.startPlayback(
            uri = "file:///tmp/voice-note.m4a",
            onProgressUpdated = {},
            onPlaybackCompleted = { completedCount += 1 },
        )

        assertEquals(1, completedCount)
    }

    private fun createManager(
        scope: TestScope,
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(scope.testScheduler),
        currentPosition: PlaybackStateHolder,
        itemFactory: AudioPlaybackItemFactory = RecordingAudioPlaybackItemFactory(),
        controllerFutureFactory: MediaControllerFutureFactory =
            MediaControllerFutureFactory { Futures.immediateFuture(controller) },
    ): AndroidAudioPlaybackManager {
        stubController(currentPosition)

        return AndroidAudioPlaybackManager(
            context = context,
            coroutineScope = scope,
            progressDispatcher = dispatcher,
            controllerFactory = controllerFutureFactory,
            controllerExecutor = directExecutor,
            mediaItemFactory = itemFactory,
        )
    }

    private fun stubController(currentPosition: PlaybackStateHolder) {
        every { context.startService(any<Intent>()) } returns null
        every { controller.addListener(capture(controllerListener)) } just runs
        every { controller.removeListener(any<Player.Listener>()) } just runs
        every { controller.setMediaItem(any()) } just runs
        every { controller.prepare() } just runs
        every { controller.play() } just runs
        every { controller.pause() } just runs
        every { controller.stop() } just runs
        every { controller.release() } just runs
        every { controller.seekTo(any<Long>()) } just runs
        every { controller.duration } returns 1_000L
        every { controller.currentPosition } answers { currentPosition.value }
        every { controller.isPlaying } returns false
        every { controller.playbackSuppressionReason } returns Player.PLAYBACK_SUPPRESSION_REASON_NONE
    }

    private class PlaybackStateHolder(
        var value: Long,
    )

    private class RecordingAudioPlaybackItemFactory : AudioPlaybackItemFactory {
        val mediaItem: MediaItem = mockk(relaxed = true)
        var lastUri: String? = null
        var lastMetadata: AudioPlaybackMetadata? = null

        override fun create(
            uri: String,
            metadata: AudioPlaybackMetadata?,
        ): MediaItem {
            lastUri = uri
            lastMetadata = metadata
            return mediaItem
        }
    }
}
