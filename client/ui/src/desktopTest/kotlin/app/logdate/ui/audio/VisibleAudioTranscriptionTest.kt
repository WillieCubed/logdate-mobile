package app.logdate.ui.audio

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.timeline.MomentAudioUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class VisibleAudioTranscriptionTest {
    @Test
    fun `visible persisted audio requests transcription once for each media revision`() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val noteId = Uuid.random()
            val audio = mutableStateOf(MomentAudioUiState("file:///first.wav", 5000, noteId = noteId))
            val requests = mutableListOf<Uuid>()
            setContent {
                LogDateTheme(dynamicColor = false) {
                    TranscriptionProvider(TranscriptionState(requestTranscription = { requests += it })) {
                        MomentAudioCard(audio.value, timeOfDay = null)
                    }
                }
            }
            runOnIdle { assertEquals(listOf(noteId), requests) }
            runOnIdle { audio.value = audio.value.copy(transcript = "The transcript arrived.") }
            runOnIdle { assertEquals(listOf(noteId), requests, "Transcript updates must not reschedule visible media") }
            runOnIdle { audio.value = audio.value.copy(uri = "file:///replacement.wav") }
            runOnIdle { assertEquals(listOf(noteId, noteId), requests) }
            runOnIdle { audio.value = audio.value.copy(durationMs = 6000) }
            runOnIdle { assertEquals(listOf(noteId, noteId, noteId), requests) }
        }

    @Test
    fun `audio without a persisted note id does not request transcription`() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val requests = mutableListOf<Uuid>()
            setContent {
                LogDateTheme(dynamicColor = false) {
                    TranscriptionProvider(TranscriptionState(requestTranscription = { requests += it })) {
                        MomentAudioCard(MomentAudioUiState("file:///unsaved.wav", 5000), timeOfDay = null)
                    }
                }
            }
            runOnIdle { assertEquals(emptyList(), requests) }
        }
}
