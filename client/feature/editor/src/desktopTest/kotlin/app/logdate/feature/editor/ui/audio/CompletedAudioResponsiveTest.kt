@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.audio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.logdate.client.media.audio.transcription.TimedTranscript
import app.logdate.client.media.audio.transcription.TimedUtterance
import app.logdate.feature.editor.ui.blocks.MemoryBlockSurface
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.feature.editor.ui.layout.LocalEditorCorners
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class CompletedAudioResponsiveTest(
    private val name: String,
    private val width: Int,
    private val height: Int,
    private val fontScale: Float,
) {
    @Test
    fun `short transcript precedes playback without filling the viewport`() =
        runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    LogDateTheme(dynamicColor = false, darkTheme = false) {
                        Box(Modifier.fillMaxSize().padding(16.dp)) {
                            MemoryBlockSurface(block, true, {}, {}, {}) {
                                AudioBlockContent(
                                    block = block,
                                    timedTranscript = shortTranscript,
                                    waveformAmplitudes = waveform,
                                    isExpanded = true,
                                    isPlaying = false,
                                    onPlayPauseClicked = {},
                                    onDeleteClicked = {},
                                    onSeekPositionChanged = {},
                                    onSeekTimestampClicked = {},
                                    showDeleteAction = false,
                                    cornerRadius = LocalEditorCorners.current.cardRadius,
                                    trailingActionInset = 40.dp,
                                    modifier = Modifier.fillMaxWidth().testTag("completed_recording"),
                                )
                            }
                        }
                    }
                }
            }
            val transcript = onNodeWithTag("completed_audio_transcript", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val player = onNodeWithContentDescription("Play").assertIsDisplayed().getUnclippedBoundsInRoot()
            val wave = onNodeWithTag("completed_audio_waveform", useUnmergedTree = true).assertIsDisplayed().getUnclippedBoundsInRoot()
            val recording = onNodeWithTag("completed_recording", useUnmergedTree = true).getUnclippedBoundsInRoot()
            onNodeWithText("00:12", useUnmergedTree = true).assertIsDisplayed()
            val timing = onNodeWithTag("audio_block_duration", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(
                timing.top >= transcript.bottom && timing.bottom <= wave.top,
                "Timing must sit above playback without covering its waveform",
            )
            assertEquals(recording.left, transcript.left, "The transcript must fill the card width")
            assertEquals(recording.right, transcript.right, "The transcript must fill the card width")
            val phrase = onNodeWithTag("completed_audio_phrase_0", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val words = onNodeWithText(shortTranscript.utterances.first().text, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals(phrase.left, words.left, "Transcript text must not reserve space for a decorative indicator")
            assertTrue(transcript.bottom <= player.top, "Text must precede the transport")
            assertEquals(
                (player.top + player.bottom) / 2,
                (wave.top + wave.bottom) / 2,
                "Playback and waveform must align at their centers",
            )
            assertEquals(player.bottom - player.top, wave.bottom - wave.top, "Playback surfaces must have identical heights")
            assertEquals(56.dp, wave.bottom - wave.top, "Playback must use the Material Expressive medium size")
            assertTrue(recording.bottom - recording.top <= (height - 32).dp, "A short transcript must not inflate to fill the pane")
            assertTrue(recording.bottom <= (height - 16).dp, "The block must fit the available pane height")
            val directory = File(System.getProperty("logdate.review.screenshots", "/private/tmp/logdate-review-screenshots"))
            directory.mkdirs()
            File(directory, "completed-audio-responsive-$name.png").writeBytes(
                requireNotNull(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()).bytes,
            )
            File(directory, "completed-audio-card-$name.png").writeBytes(
                requireNotNull(
                    Image.makeFromBitmap(onNodeWithTag("memory_block_${block.id}").captureToImage().asSkiaBitmap()).encodeToData(),
                ).bytes,
            )
        }

    @Test
    fun `long transcript scrolls without displacing playback`() =
        runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
            val longTranscript =
                TimedTranscript(
                    List(20) {
                        TimedUtterance(
                            "Memory number $it, walking through the quiet streets after the rain.",
                            it * 600L,
                            (it + 1) * 600L,
                        )
                    },
                )
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    LogDateTheme(dynamicColor = false) {
                        Box(Modifier.fillMaxSize().padding(16.dp)) {
                            AudioBlockContent(
                                block = block,
                                timedTranscript = longTranscript,
                                isExpanded = true,
                                isPlaying = false,
                                onPlayPauseClicked = {},
                                onDeleteClicked = {},
                                onSeekPositionChanged = {},
                                onSeekTimestampClicked = {},
                                showDeleteAction = false,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            val playerBefore = onNodeWithContentDescription("Play").assertIsDisplayed().getUnclippedBoundsInRoot()
            val transcript = onNodeWithTag("completed_audio_transcript", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(transcript.bottom <= playerBefore.top, "Long text must not push playback above the transcript")
            onNodeWithText(longTranscript.utterances.last().text).performScrollTo().assertIsDisplayed()
            assertEquals(playerBefore, onNodeWithContentDescription("Play").assertIsDisplayed().getUnclippedBoundsInRoot())
            onNodeWithText("00:12", useUnmergedTree = true).assertIsDisplayed()
        }

    @Test
    fun `waveform exposes accessible seeking without a second progress control`() =
        runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
            var requestedProgress: Float? = null
            setContent {
                LogDateTheme(dynamicColor = false) {
                    AudioBlockContent(
                        block = block,
                        isExpanded = true,
                        isPlaying = false,
                        onPlayPauseClicked = {},
                        onDeleteClicked = {},
                        onSeekPositionChanged = { requestedProgress = it },
                        onSeekTimestampClicked = {},
                        showDeleteAction = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            onNodeWithTag(
                "completed_audio_waveform",
                useUnmergedTree = true,
            ).performSemanticsAction(SemanticsActions.SetProgress) { it(.75f) }
            assertEquals(.75f, requestedProgress)
            assertEquals(1, onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).fetchSemanticsNodes().size)
        }

    @Test
    fun `missing transcript leaves a compact player`() =
        runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
            setContent {
                LogDateTheme(dynamicColor = false) {
                    AudioBlockContent(
                        block = block.copy(transcription = ""),
                        isExpanded = true,
                        isPlaying = false,
                        onPlayPauseClicked = {},
                        onDeleteClicked = {},
                        onSeekPositionChanged = {},
                        onSeekTimestampClicked = {},
                        showDeleteAction = false,
                        trailingActionInset = 40.dp,
                        modifier = Modifier.fillMaxWidth().testTag("completed_recording"),
                    )
                }
            }
            onNodeWithTag("completed_audio_transcript", useUnmergedTree = true).assertDoesNotExist()
            onNodeWithContentDescription("Play").assertIsDisplayed()
            val recording = onNodeWithTag("completed_recording", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(recording.bottom - recording.top < 160.dp)
            val timing = onNodeWithTag("audio_block_duration", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val surface = onNodeWithTag("completed_audio_waveform_surface", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals(surface.right, timing.right, "Timing and waveform must align beside the options menu")
        }

    @Test
    fun `compact waveform retains room for amplitudes at larger text scales`() =
        runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    LogDateTheme(dynamicColor = false, darkTheme = false) {
                        AudioBlockContent(
                            block = block,
                            timedTranscript = shortTranscript,
                            waveformAmplitudes = waveform,
                            isExpanded = false,
                            isPlaying = false,
                            onPlayPauseClicked = {},
                            onDeleteClicked = {},
                            onSeekPositionChanged = {},
                            onSeekTimestampClicked = {},
                            showDeleteAction = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            val wave = onNodeWithTag("completed_audio_waveform", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(
                wave.bottom - wave.top >= 56.dp,
                "Compact timing must not reduce waveform height",
            )
            val directory = File(System.getProperty("logdate.review.screenshots", "/private/tmp/logdate-review-screenshots"))
            directory.mkdirs()
            File(directory, "completed-audio-compact-$name.png").writeBytes(
                requireNotNull(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()).bytes,
            )
        }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> =
            listOf(
                arrayOf("phone", 390, 780, 1f),
                arrayOf("narrow-pane", 320, 640, 1f),
                arrayOf("foldable-pane", 360, 520, 1f),
                arrayOf("tablet", 720, 800, 1f),
                arrayOf("landscape", 720, 300, 1f),
                arrayOf("large-text", 320, 640, 2f),
            )
    }
}

private val block =
    AudioBlockUiState(
        captureState = AudioCaptureState.Ready("file:///responsive-fixture.m4a", 12000),
        transcription = "The streets were quiet after the rain. We took the long way home.",
    )
private val shortTranscript =
    TimedTranscript(
        listOf(
            TimedUtterance("The streets were quiet after the rain.", 0, 5999),
            TimedUtterance("We took the long way home.", 6000, 12000),
        ),
    )
private val waveform = List(120) { index -> .15f + (index % 9) * .09f }
