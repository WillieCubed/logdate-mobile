package app.logdate.feature.editor.ui.audio

import app.logdate.client.media.audio.AudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioPlaybackStatus
import app.logdate.client.media.audio.AudioPlaybackStatusProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlaybackOwnershipTest {
    @Test
    fun stalePreviewCannotPauseSeekOrStopTheNewPlaybackOwner() =
        runTest {
            val player = SharedPlayer()
            val first = MutableStateFlow(AudioUiState())
            val second = MutableStateFlow(AudioUiState())
            val a = AudioPlaybackController(first, player, player, backgroundScope) {}
            val b = AudioPlaybackController(second, player, player, backgroundScope) {}
            a.start("first.m4a", null)
            runCurrent()
            b.start("second.m4a", null)
            runCurrent()
            assertFalse(first.value.isPlaying)
            assertTrue(second.value.isPlaying)
            a.pause()
            a.seek(.5f)
            a.stopIfPlaying()
            runCurrent()
            assertEquals("second.m4a", player.playbackStatus.value.currentUri)
            assertTrue(player.playbackStatus.value.isPlaying)
            assertEquals(0, player.pauseCalls)
            assertEquals(0, player.seekCalls)
            assertEquals(0, player.stopCalls)
        }

    @Test
    fun anOldPreviewOfTheSharedPlayerCannotControlTheCurrentUri() =
        runTest {
            val player = SharedPlayer()
            val state = MutableStateFlow(AudioUiState())
            val controller = AudioPlaybackController(state, player, player, backgroundScope) {}
            controller.start("first.m4a", null)
            runCurrent()
            controller.start("second.m4a", null)
            runCurrent()
            controller.pause("first.m4a")
            controller.seek(.5f, "first.m4a")
            runCurrent()
            assertEquals(0, player.pauseCalls)
            assertEquals(0, player.seekCalls)
            controller.toggle("first.m4a", null)
            runCurrent()
            assertEquals("first.m4a", player.playbackStatus.value.currentUri)
            assertTrue(state.value.isPlaying)
        }

    private class SharedPlayer :
        AudioPlaybackManager,
        AudioPlaybackStatusProvider {
        override val playbackStatus = MutableStateFlow(AudioPlaybackStatus())
        var pauseCalls = 0
        var seekCalls = 0
        var stopCalls = 0

        override fun startPlayback(
            uri: String,
            metadata: AudioPlaybackMetadata?,
            onProgressUpdated: (Float) -> Unit,
            onPlaybackCompleted: () -> Unit,
        ) {
            playbackStatus.value = AudioPlaybackStatus(currentUri = uri, isPlaying = true)
        }

        override fun pausePlayback() {
            pauseCalls++
            playbackStatus.value = playbackStatus.value.copy(isPlaying = false)
        }

        override fun seekTo(position: Float) {
            seekCalls++
        }

        override fun stopPlayback() {
            stopCalls++
            playbackStatus.value = AudioPlaybackStatus()
        }

        override fun release() = Unit
    }
}
