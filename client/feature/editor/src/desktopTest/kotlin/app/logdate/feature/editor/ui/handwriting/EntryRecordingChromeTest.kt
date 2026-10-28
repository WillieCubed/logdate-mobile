package app.logdate.feature.editor.ui.handwriting

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.client.datastore.EntryStartMode
import app.logdate.feature.editor.ui.audio.ActiveRecordingDisplay
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.layout.LocalEntryChromeHeight
import app.logdate.feature.editor.ui.layout.entryContentPadding
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class EntryRecordingChromeTest {
    @Test
    fun recordingTransportOwnsBottomControlsAndReceivesPointerInput() =
        runDesktopComposeUiTest(width = 400, height = 800) {
            val block = AudioBlockUiState(captureState = AudioCaptureState.Recording())
            var finishes = 0
            setContent {
                LogDateTheme(dynamicColor = false) {
                    EntryPageChrome(
                        EditorState(blocks = listOf(block), expandedBlockId = block.id),
                        EntryPageOverviewState(false),
                        EntryStartMode.AUDIO,
                        {},
                        {},
                        {},
                        header = {},
                    ) {
                        ActiveRecordingDisplay(
                            listOf(0.1f, 0.4f, 0.2f),
                            15.seconds,
                            {},
                            {},
                            { finishes++ },
                            Modifier.fillMaxSize().entryContentPadding(top = LocalEntryChromeHeight.current).testTag("recording_content"),
                            transcriptionText = "A thought to keep.",
                        )
                    }
                }
            }
            val content = onNodeWithTag("recording_content").getUnclippedBoundsInRoot()
            assertTrue(content.top >= 64.dp, "Recording content overlaps the persistent header: $content")
            onNodeWithTag("entry_capture_tools").assertDoesNotExist()
            onNodeWithText("Finish").performTouchInput { click() }
            runOnIdle { assertEquals(1, finishes) }
        }
}
