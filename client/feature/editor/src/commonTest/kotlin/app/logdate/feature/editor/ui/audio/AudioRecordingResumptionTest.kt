package app.logdate.feature.editor.ui.audio

import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.media.audio.transcription.TranscriptionService
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.feature.editor.ui.editor.delegate.DefaultPendingAudioRecoverer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.uuid.Uuid

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
    fun `a starting recording reattaches without consuming its unfinished file`() =
        runTest {
            val manager = RecoverableRecordingManager(active = false, starting = true)
            val recoverer = DefaultPendingAudioRecoverer({ error("Must not parse a starting recording") }, manager)
            assertIs<AudioCaptureState.Recording>(recoverer.recover(AudioCaptureState.Stopping(manager.currentRecordingPath)))
            assertEquals(0, manager.stopCalls)
        }

    @Test
    fun `live recording is reattached before attempting file recovery`() =
        runTest {
            val manager = RecoverableRecordingManager(active = true)
            val recoverer = DefaultPendingAudioRecoverer({ error("Must not parse an active recording") }, manager)
            val recovered = recoverer.recover(AudioCaptureState.Stopping(manager.currentRecordingPath))
            assertIs<AudioCaptureState.Recording>(recovered)
            assertEquals(0, manager.stopCalls)
        }

    @Test
    fun `completed owner recording is handed off before file recovery`() =
        runTest {
            val manager = RecoverableRecordingManager(active = false)
            val recoverer = DefaultPendingAudioRecoverer({ 1000L }, manager)
            assertIs<AudioCaptureState.Ready>(recoverer.recover(AudioCaptureState.Stopping(manager.currentRecordingPath)))
            assertEquals(1, manager.stopCalls)
        }

    @Test
    fun `cancellation does not mark an intact recording as lost`() =
        runTest {
            val recoverer = DefaultPendingAudioRecoverer({ throw CancellationException("Cancelled recovery") })
            kotlin.test.assertFailsWith<CancellationException> {
                recoverer.recover(AudioCaptureState.Stopping("/audio.m4a"))
            }
        }

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

private class RecoverableRecordingManager(
    private val active: Boolean,
    private val starting: Boolean = false,
) : AudioRecordingManager {
    var stopCalls = 0
    override val isRecording: Boolean get() = active
    override val isStartingRecording: Boolean get() = starting
    override val currentRecordingPath: String get() = "/audio.m4a"

    override suspend fun startRecording(targetNoteId: Uuid?): Boolean = false

    override suspend fun stopRecording(): String {
        stopCalls++
        return "file:///audio.m4a"
    }

    override fun getAudioLevelFlow(): Flow<Float> = emptyFlow()

    override fun getRecordingDurationFlow(): Flow<Duration> = emptyFlow()

    override fun getTranscriptionFlow(): Flow<String?> = emptyFlow()

    override fun setTranscriptionService(service: TranscriptionService) = Unit

    override fun release() = Unit
}
