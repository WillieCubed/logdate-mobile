package app.logdate.feature.editor.ui.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.audio.ActiveRecordingDisplay
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class MemoryRecordingLayoutTest {
    @Test
    fun `portrait recording keeps transport and Add available`() = verifyRecordingLayout(Size(390f, 780f))

    @Test
    fun `landscape recording controls and Add remain reachable through entry scrolling`() = verifyRecordingLayout(Size(700f, 300f))

    private fun verifyRecordingLayout(size: Size) =
        runSkikoComposeUiTest(size = size) {
            var finished = false
            val block = AudioBlockUiState()
            setContent {
                MaterialTheme {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("recording_entry"),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            MemoryBlockSurface(block, true, {}, {}, {}) {
                                ActiveRecordingDisplay(
                                    audioLevels = listOf(0.2f, 0.4f),
                                    recordingDuration = 5.seconds,
                                    onRestart = {},
                                    onPause = {},
                                    onFinish = { finished = true },
                                    transcriptionText = "This is a recorded memory.",
                                    modifier = Modifier.height(unfinishedAudioHeight(size.height.dp, size.width.dp)),
                                )
                            }
                        }
                        item { EndOfEntryAddControl(0f, false, {}, {}, {}) }
                    }
                }
            }
            if (size.height < 500f) {
                onNodeWithTag("recording_entry").performTouchInput {
                    swipe(Offset(4f, height - 24f), Offset(4f, 24f))
                }
                waitForIdle()
            }
            onNodeWithText("Finish").assertIsDisplayed().performClick()
            assertTrue(finished)
            onNodeWithContentDescription("Restart recording").assertDoesNotExist()
            onNodeWithContentDescription("Pause").assertIsDisplayed()
            onNodeWithTag("add_to_entry").performScrollTo().assertIsDisplayed()
            if (size.height >= 500f) {
                val directory = File(System.getProperty("logdate.review.screenshots", "build/reports/editor-screenshots"))
                directory.mkdirs()
                File(directory, "recording-portrait-offscreen.png").writeBytes(
                    requireNotNull(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()).bytes,
                )
            }
        }
}
