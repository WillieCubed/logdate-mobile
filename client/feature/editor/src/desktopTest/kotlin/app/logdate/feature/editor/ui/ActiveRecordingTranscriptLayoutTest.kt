package app.logdate.feature.editor.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.logdate.feature.editor.ui.audio.ActiveRecordingDisplay
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class ActiveRecordingTranscriptLayoutTest {
    @Test
    fun `failure collapses the transcript and promotes audio without losing captured text or controlling the take`() =
        runSkikoComposeUiTest(size = Size(390f, 780f)) {
            val failed = mutableStateOf(false)
            var restarts = 0
            var pauses = 0
            var finishes = 0
            setContent {
                LogDateTheme(dynamicColor = false) {
                    ActiveRecordingDisplay(
                        List(40) { .5f },
                        42.seconds,
                        { restarts++ },
                        { pauses++ },
                        { finishes++ },
                        transcriptionText = "A memory worth keeping.",
                        transcriptionHasError = failed.value,
                    )
                }
            }
            val transcript = onNodeWithTag("recording_transcript_surface").getUnclippedBoundsInRoot()
            val waveform = onNodeWithTag("recording_waveform").getUnclippedBoundsInRoot()
            val transcriptHeight = transcript.bottom - transcript.top
            val waveformHeight = waveform.bottom - waveform.top
            assertTrue(transcriptHeight > waveformHeight * 3, "Healthy transcription is the main content")
            onNodeWithText("Pause").assertDoesNotExist()
            onNodeWithContentDescription("Pause").assertIsDisplayed()
            failed.value = true
            waitForIdle()
            val collapsed = onNodeWithTag("recording_transcript_surface").getUnclippedBoundsInRoot()
            val focusedWaveform = onNodeWithTag("recording_waveform").getUnclippedBoundsInRoot()
            assertTrue(collapsed.bottom - collapsed.top < transcriptHeight / 3, "Failure releases the transcript's space")
            assertTrue(focusedWaveform.bottom - focusedWaveform.top > waveformHeight * 3, "Audio takes the released space")
            onNodeWithText("A memory worth keeping.").assertDoesNotExist()
            onNodeWithContentDescription("Show transcript").performClick()
            onNodeWithText("A memory worth keeping.").assertIsDisplayed()
            onNodeWithContentDescription("Hide transcript").performClick()
            onNodeWithText("A memory worth keeping.").assertDoesNotExist()
            failed.value = false
            waitForIdle()
            onNodeWithText("A memory worth keeping.").assertIsDisplayed()
            onNodeWithContentDescription("Show transcript").assertDoesNotExist()
            val recovered = onNodeWithTag("recording_waveform").getUnclippedBoundsInRoot()
            assertEquals(waveformHeight, recovered.bottom - recovered.top)
            assertEquals(0, restarts)
            assertEquals(0, pauses)
            assertEquals(0, finishes)
            onNodeWithContentDescription("Pause").performClick()
            onNodeWithText("Finish").performClick()
            assertEquals(1, pauses)
            assertEquals(1, finishes)
        }

    @Test
    fun `failed transcription without captured text leaves a compact status and reachable transport`() =
        runSkikoComposeUiTest(size = Size(390f, 640f)) {
            setContent {
                LogDateTheme(dynamicColor = false) {
                    ActiveRecordingDisplay(
                        List(40) { .5f },
                        42.seconds,
                        {},
                        {},
                        {},
                        transcriptionHasError = true,
                    )
                }
            }
            onNodeWithContentDescription("Show transcript").assertDoesNotExist()
            onNodeWithText("Transcription failed. The audio is unaffected.").assertDoesNotExist()
            onNodeWithText("Recording").assertIsDisplayed()
            onNodeWithContentDescription("Pause").assertIsDisplayed()
            onNodeWithText("Finish").assertIsDisplayed()
            val transcript = onNodeWithTag("recording_transcript_surface").getUnclippedBoundsInRoot()
            val waveform = onNodeWithTag("recording_waveform").getUnclippedBoundsInRoot()
            assertTrue(waveform.bottom - waveform.top > (transcript.bottom - transcript.top) * 2)
        }
}
