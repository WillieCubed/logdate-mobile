package app.logdate.feature.editor.ui.audio

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CompletedAudioSeekingTest {
    @Test
    fun `RTL waveform follows seeking direction`() =
        runSkikoComposeUiTest {
            val progress = mutableStateOf(.25f)
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MaterialTheme(
                        colorScheme =
                            lightColorScheme(
                                primary = Color.Red,
                                primaryContainer = Color.White,
                                onPrimaryContainer = Color.Blue,
                            ),
                    ) {
                        AudioBlockContent(
                            block = AudioBlockUiState(captureState = AudioCaptureState.Ready("file:///rtl.m4a", 12000)),
                            isExpanded = true,
                            isPlaying = false,
                            playbackProgress = progress.value,
                            waveformAmplitudes = List(300) { .8f },
                            onPlayPauseClicked = {},
                            onDeleteClicked = {},
                            onSeekPositionChanged = { progress.value = it },
                            onSeekTimestampClicked = {},
                            showDeleteAction = false,
                        )
                    }
                }
            }
            val waveform = onNodeWithTag("completed_audio_waveform")
            val pixels = waveform.captureToImage().toPixelMap()

            fun playedPixels(
                start: Int,
                end: Int,
            ): Int =
                (start until end).sumOf { x ->
                    (pixels.height / 3 until pixels.height * 2 / 3).count { y -> pixels[x, y].toArgb() == Color.Red.toArgb() }
                }
            assertTrue(
                playedPixels(pixels.width * 4 / 5, pixels.width - 12) > playedPixels(12, pixels.width / 5),
                "The played quarter must be on the same side as the RTL seek thumb",
            )
            waveform.performTouchInput { click(center.copy(x = center.x * 1.6f)) }
            runOnIdle { assertTrue(progress.value in 0.15f..0.25f, "A tap on the right must seek near the recording start") }
        }

    @Test
    fun `recording with no duration cannot seek`() =
        runSkikoComposeUiTest {
            setContent {
                MaterialTheme {
                    AudioBlockContent(
                        block = AudioBlockUiState(captureState = AudioCaptureState.Ready("file:///empty.m4a", 0)),
                        isExpanded = true,
                        isPlaying = false,
                        onPlayPauseClicked = {},
                        onDeleteClicked = {},
                        onSeekPositionChanged = {},
                        onSeekTimestampClicked = {},
                        showDeleteAction = false,
                        modifier = Modifier,
                    )
                }
            }
            onNodeWithTag("completed_audio_waveform").assertIsNotEnabled()
        }
}
