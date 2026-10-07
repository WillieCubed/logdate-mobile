@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.audio

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.client.media.audio.transcription.TimedTranscript
import app.logdate.client.media.audio.transcription.TimedUtterance
import app.logdate.client.media.device.DefaultMediaDevices
import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CompletedAudioPresentationTest {
    @Test
    fun `completed recording gives transcript space without search or caption editing`() =
        runSkikoComposeUiTest(size = Size(390f, 640f)) {
            var playbackRequests = 0
            var seekMs: Long? = null
            setContent {
                CompletedAudioFixture {
                    AudioBlockContent(
                        block = completedBlock,
                        waveformAmplitudes = reviewWaveform,
                        isExpanded = true,
                        isPlaying = true,
                        timedTranscript = transcript,
                        playbackProgress = .4f,
                        onPlayPauseClicked = { playbackRequests++ },
                        onDeleteClicked = {},
                        onSeekPositionChanged = {},
                        onSeekTimestampClicked = { seekMs = it },
                        showDeleteAction = false,
                        modifier = Modifier.fillMaxWidth().height(560.dp),
                    )
                }
            }
            onNodeWithText("Search transcript").assertDoesNotExist()
            assertEquals(0, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
            onNodeWithText("The streets were quiet after the rain.").assertIsDisplayed()
            onNodeWithTag("completed_audio_phrase_0").assertIsSelected()
            onNodeWithText("We took the long way home.").assertIsDisplayed().performClick()
            assertEquals(6000L, seekMs)
            onNodeWithContentDescription("Pause").assertIsDisplayed().performClick()
            assertEquals(1, playbackRequests)
            val waveform = onNodeWithTag("completed_audio_waveform").getUnclippedBoundsInRoot()
            val transcriptBounds = onNodeWithTag("completed_audio_transcript").getUnclippedBoundsInRoot()
            assertTrue(
                transcriptBounds.bottom - transcriptBounds.top > waveform.bottom - waveform.top,
                "The transcript is the main content",
            )
            val directory =
                File(System.getProperty("logdate.review.screenshots", "/private/tmp/logdate-review-screenshots"))
            directory.mkdirs()
            File(directory, "completed-audio-transcript-portrait.png").writeBytes(
                requireNotNull(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()).bytes,
            )
        }

    @Test
    fun `compact recording keeps a transcript excerpt visible`() =
        runSkikoComposeUiTest(size = Size(390f, 200f)) {
            setContent {
                CompletedAudioFixture {
                    AudioBlockContent(
                        block = completedBlock,
                        waveformAmplitudes = reviewWaveform,
                        isExpanded = false,
                        isPlaying = false,
                        onPlayPauseClicked = {},
                        onDeleteClicked = {},
                        onSeekPositionChanged = {},
                        onSeekTimestampClicked = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            onNodeWithText(completedBlock.transcription).assertIsDisplayed()
            onNodeWithContentDescription("Play").assertIsDisplayed()
        }

    @Test
    fun `completed playback omits output picker when no alternative output exists`() =
        runSkikoComposeUiTest(size = Size(390f, 640f)) {
            setContent {
                CompletedAudioFixture {
                    AudioBlockContent(
                        block = completedBlock,
                        waveformAmplitudes = reviewWaveform,
                        isExpanded = true,
                        isPlaying = false,
                        onPlayPauseClicked = {},
                        onDeleteClicked = {},
                        onSeekPositionChanged = {},
                        onSeekTimestampClicked = {},
                        outputSelection =
                            MediaDeviceSelectionUiState(
                                kind = MediaDeviceKind.AUDIO_OUTPUT,
                                devices = listOf(DefaultMediaDevices.systemOutput),
                                selectedDeviceId = DefaultMediaDevices.systemOutput.id,
                            ),
                        modifier = Modifier.fillMaxWidth().height(560.dp),
                    )
                }
            }
            onNodeWithText("System output").assertDoesNotExist()
            onNodeWithContentDescription("Play").assertIsDisplayed()
        }

    @Test
    fun `completed playback offers output selection when another available route exists`() =
        runSkikoComposeUiTest(size = Size(390f, 640f)) {
            setContent {
                CompletedAudioFixture {
                    AudioBlockContent(
                        block = completedBlock,
                        waveformAmplitudes = reviewWaveform,
                        isExpanded = true,
                        isPlaying = false,
                        onPlayPauseClicked = {},
                        onDeleteClicked = {},
                        onSeekPositionChanged = {},
                        onSeekTimestampClicked = {},
                        outputSelection =
                            MediaDeviceSelectionUiState(
                                kind = MediaDeviceKind.AUDIO_OUTPUT,
                                devices =
                                    listOf(
                                        DefaultMediaDevices.systemOutput,
                                        MediaDeviceUiState(
                                            "headphones",
                                            "Headphones",
                                            MediaDeviceKind.AUDIO_OUTPUT,
                                            MediaDeviceCategory.WIRED,
                                        ),
                                    ),
                                selectedDeviceId = DefaultMediaDevices.systemOutput.id,
                            ),
                        modifier = Modifier.fillMaxWidth().height(560.dp),
                    )
                }
            }
            onNodeWithText("System output").assertIsDisplayed()
        }

    @Test
    fun `empty live timed transcript preserves the saved transcript`() =
        runSkikoComposeUiTest(size = Size(390f, 640f)) {
            setContent {
                CompletedAudioFixture {
                    AudioBlockContent(
                        block = completedBlock,
                        waveformAmplitudes = reviewWaveform,
                        isExpanded = true,
                        isPlaying = false,
                        timedTranscript = TimedTranscript(emptyList()),
                        onPlayPauseClicked = {},
                        onDeleteClicked = {},
                        onSeekPositionChanged = {},
                        onSeekTimestampClicked = {},
                        modifier = Modifier.fillMaxWidth().height(560.dp),
                    )
                }
            }
            onNodeWithText(completedBlock.transcription).assertIsDisplayed()
        }

    @Test
    fun `short pane keeps transcript readable with alternate output`() =
        runSkikoComposeUiTest(size = Size(390f, 200f)) {
            setContent {
                CompletedAudioFixture {
                    AudioBlockContent(
                        block = completedBlock,
                        timedTranscript = transcript,
                        isExpanded = true,
                        isPlaying = false,
                        onPlayPauseClicked = {},
                        onDeleteClicked = {},
                        onSeekPositionChanged = {},
                        onSeekTimestampClicked = {},
                        showDeleteAction = false,
                        availableHeight = 200.dp,
                        outputSelection =
                            MediaDeviceSelectionUiState(
                                kind = MediaDeviceKind.AUDIO_OUTPUT,
                                devices =
                                    listOf(
                                        DefaultMediaDevices.systemOutput,
                                        MediaDeviceUiState(
                                            "headphones",
                                            "Headphones",
                                            MediaDeviceKind.AUDIO_OUTPUT,
                                            MediaDeviceCategory.WIRED,
                                        ),
                                    ),
                                selectedDeviceId = DefaultMediaDevices.systemOutput.id,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            val text = onNodeWithTag("completed_audio_transcript").getUnclippedBoundsInRoot()
            assertTrue(text.bottom - text.top >= 56.dp, "At least one transcript line and its padding must remain visible")
            onNodeWithText(transcript.utterances.first().text).assertIsDisplayed()
            onNodeWithContentDescription("Play").performScrollTo().assertIsDisplayed()
            onNodeWithText("System output").performScrollTo().assertIsDisplayed()
        }

    private val completedBlock =
        AudioBlockUiState(
            captureState = AudioCaptureState.Ready("file:///synthetic-review-recording.m4a", 12000),
            transcription = "The streets were quiet after the rain. We took the long way home.",
        )

    private val transcript =
        TimedTranscript(
            listOf(
                TimedUtterance("The streets were quiet after the rain.", 0, 5999),
                TimedUtterance("We took the long way home.", 6000, 12000),
            ),
        )
}

@Composable
private fun CompletedAudioFixture(content: @Composable () -> Unit) {
    LogDateTheme(dynamicColor = false) {
        content()
    }
}

private val reviewWaveform = List(60) { index -> .18f + (index % 9) * .08f }
