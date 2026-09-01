package app.logdate.feature.editor.ui.audio

import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.feature.editor.ui.editor.delegate.DefaultPendingAudioRecoverer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests recovery of audio blocks left in [AudioCaptureState.Stopping] after
 * a draft is reloaded across a process boundary.
 *
 * The recoverer's contract: validate the file referenced by [AudioCaptureState.Stopping.filePath]
 * via [app.logdate.client.media.audio.AudioDurationResolver] and produce a definitive
 * [AudioCaptureState.Ready] or [AudioCaptureState.Failed]. It must not throw.
 */
class AudioRecordingResumptionTest {
    @Test
    fun `recover with parseable file returns ready`() =
        runTest {
            val recoverer =
                DefaultPendingAudioRecoverer(
                    durationResolver = { _ -> 12_345L },
                )

            val resolved =
                recoverer.recover(
                    AudioCaptureState.Stopping(filePath = "file:///audio_notes/recovered.m4a"),
                )

            val ready = assertIs<AudioCaptureState.Ready>(resolved)
            assertEquals("file:///audio_notes/recovered.m4a", ready.uri)
            assertEquals(12_345L, ready.durationMs)
        }

    @Test
    fun `recover with unparseable file returns failed`() =
        runTest {
            val recoverer =
                DefaultPendingAudioRecoverer(
                    durationResolver = { _ -> null },
                )

            val resolved =
                recoverer.recover(
                    AudioCaptureState.Stopping(filePath = "file:///audio_notes/corrupt.m4a"),
                )

            assertIs<AudioCaptureState.Failed>(resolved)
        }

    @Test
    fun `recover with null file path returns failed`() =
        runTest {
            val recoverer =
                DefaultPendingAudioRecoverer(
                    durationResolver = { _ -> 1_000L },
                )

            val resolved =
                recoverer.recover(AudioCaptureState.Stopping(filePath = null))

            assertIs<AudioCaptureState.Failed>(resolved)
        }

    @Test
    fun `recover when resolver throws returns failed`() =
        runTest {
            val recoverer =
                DefaultPendingAudioRecoverer(
                    durationResolver = { _ -> throw IllegalStateException("disk read failed") },
                )

            val resolved =
                recoverer.recover(
                    AudioCaptureState.Stopping(filePath = "file:///audio_notes/exception.m4a"),
                )

            assertIs<AudioCaptureState.Failed>(resolved)
        }
}
