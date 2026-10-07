package app.logdate.feature.editor.ui.blocks

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RecordingCaptionTest {
    @Test
    fun `unfinished audio does not expose a caption editor or overwrite its caption`() =
        runSkikoComposeUiTest {
            val block = AudioBlockUiState(captureState = AudioCaptureState.Recording(), caption = "Retained caption")
            var updates = 0
            setContent {
                LogDateTheme(dynamicColor = false) {
                    MemoryCaptionField(block, {}, { updates++ }, editRequest = 1)
                }
            }
            onNodeWithTag("memory_caption_${block.id}").assertDoesNotExist()
            assertEquals(0, updates)
            assertEquals("Retained caption", block.caption)
        }

    @Test
    fun `completed audio keeps the existing caption available`() =
        runSkikoComposeUiTest {
            val block =
                AudioBlockUiState(captureState = AudioCaptureState.Ready("file:///recording.m4a", 5000), caption = "Retained caption")
            setContent {
                LogDateTheme(dynamicColor = false) {
                    MemoryCaptionField(block, {}, {}, editRequest = 0)
                }
            }
            onNodeWithText("Retained caption").assertIsDisplayed()
        }
}
