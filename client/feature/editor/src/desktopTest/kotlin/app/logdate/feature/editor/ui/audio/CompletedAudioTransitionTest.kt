package app.logdate.feature.editor.ui.audio

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.LocalSharedTransitionScope
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class)
class CompletedAudioTransitionTest {
    @Test
    fun `completed playback and waveform share a transition when expanded and collapsed`() =
        runSkikoComposeUiTest(size = Size(390f, 640f)) {
            val expanded = mutableStateOf(false)
            val block =
                AudioBlockUiState(
                    captureState = AudioCaptureState.Ready("file:///synthetic-transition.m4a", 12000),
                    transcription = "A recorded memory with a readable transcript.",
                )
            var sharedScope: SharedTransitionScope? = null
            mainClock.autoAdvance = false
            setContent {
                LogDateTheme(dynamicColor = false) {
                    SharedTransitionLayout {
                        sharedScope = this
                        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                            AudioBlockContent(
                                block = block,
                                waveformAmplitudes = List(60) { .5f },
                                isExpanded = expanded.value,
                                isPlaying = false,
                                onPlayPauseClicked = {},
                                onDeleteClicked = {},
                                onSeekPositionChanged = {},
                                onSeekTimestampClicked = {},
                                showDeleteAction = false,
                                modifier = Modifier.fillMaxWidth().height(420.dp),
                            )
                        }
                    }
                }
            }
            waitForIdle()
            assertFalse(requireNotNull(sharedScope).isTransitionActive)
            runOnIdle { expanded.value = true }
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(64)
            waitForIdle()
            assertTrue(requireNotNull(sharedScope).isTransitionActive, "Expansion must animate shared playback and waveform bounds")
            mainClock.advanceTimeBy(2000)
            waitForIdle()
            assertFalse(requireNotNull(sharedScope).isTransitionActive)
            runOnIdle { expanded.value = false }
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(64)
            waitForIdle()
            assertTrue(requireNotNull(sharedScope).isTransitionActive, "Collapse must animate the same shared elements back")
            mainClock.advanceTimeBy(2000)
            waitForIdle()
            assertFalse(requireNotNull(sharedScope).isTransitionActive)
        }
}
