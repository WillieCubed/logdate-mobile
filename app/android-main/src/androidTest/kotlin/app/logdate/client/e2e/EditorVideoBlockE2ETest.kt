package app.logdate.client.e2e

import android.content.Context
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.feature.editor.ui.MainEditorContent
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.VideoBlockUiState
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.feature.editor.ui.video.VideoPlayerTags
import app.logdate.shared.model.Journal
import app.logdate.ui.media.MediaDeviceSelectorTags
import app.logdate.ui.theme.LogDateTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Clock
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class EditorVideoBlockE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<VideoPlaybackHostActivity>()

    @Test
    fun compactEditorVideoBlockExposesPlaybackRouteAndPipControls() {
        assumeTrue(screenWidthDp() < 600)
        setEditorVideoContent(expandVideoBlock = false)

        composeRule.waitForIdle()
        assertEditorVideoControlsAreUsable()
    }

    @Test
    fun expandedEditorVideoBlockExposesPlaybackRouteAndPipControls() {
        assumeTrue(screenWidthDp() >= 600)
        setEditorVideoContent(expandVideoBlock = true)

        composeRule.waitForIdle()
        assertEditorVideoControlsAreUsable()
    }

    private fun assertEditorVideoControlsAreUsable() {
        composeRule.onNodeWithTag(VideoPlayerTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoPlayerTags.PIP_BUTTON).assertIsDisplayed().assertHasClickAction()
        composeRule.onAllNodesWithTag(MediaDeviceSelectorTags.chip("Audio output"))[0]
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeRule.onNodeWithText("Open output switcher").assertIsDisplayed()
        composeRule
            .onNodeWithTag(MediaDeviceSelectorTags.systemSettingsButton("Audio output"))
            .assertIsDisplayed()
            .assertHasClickAction()

        pressBack()
    }

    private fun setEditorVideoContent(expandVideoBlock: Boolean) {
        val videoBlockId = Uuid.random()
        val videoBlock =
            VideoBlockUiState(
                id = videoBlockId,
                uri = emptyVideoFileUri(),
                caption = "Walkthrough clip",
                durationMs = 84_000L,
            )
        val journal = Journal(title = "Field notes")

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                LogDateTheme(dynamicColor = false) {
                    MainEditorContent(
                        uiState =
                            BlocksUiState(
                                blocks = listOf(videoBlock),
                                expandedBlockId = if (expandVideoBlock) videoBlockId else null,
                                availableJournals = listOf(journal),
                                selectedJournalIds = listOf(journal.id),
                                onBlockFocused = {},
                                onJournalSelectionChanged = {},
                                onUpdateBlock = {},
                                onCreateBlock = { _: BlockType, id: Uuid -> TextBlockUiState(id = id) },
                                onDeleteBlock = {},
                            ),
                        shouldReturnToPickerOnBack = false,
                        onDismissExpanded = {},
                    )
                }
            }
        }
    }

    private fun emptyVideoFileUri(): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "editor-video-block.mp4")
        if (!file.exists()) {
            file.writeBytes(ByteArray(0))
        }
        return Uri.fromFile(file).toString()
    }

    private fun screenWidthDp(): Int =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .resources
            .configuration
            .screenWidthDp
}
