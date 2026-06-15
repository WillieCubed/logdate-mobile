package app.logdate.client.e2e

import android.content.Context
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.feature.editor.ui.video.VideoPlayerTags
import app.logdate.feature.journals.ui.detail.VideoNoteViewerContent
import app.logdate.ui.media.MediaDeviceSelectorTags
import app.logdate.ui.theme.LogDateTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NoteViewerVideoE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<VideoPlaybackHostActivity>()

    @Test
    fun compactVideoNoteViewerExposesPlaybackRouteAndPipControls() {
        assumeTrue(screenWidthDp() < 600)
        setVideoNoteViewerContent()

        composeRule.waitForIdle()
        assertVideoNoteViewerControlsAreUsable()
    }

    @Test
    fun expandedVideoNoteViewerExposesPlaybackRouteAndPipControls() {
        assumeTrue(screenWidthDp() >= 600)
        setVideoNoteViewerContent()

        composeRule.waitForIdle()
        assertVideoNoteViewerControlsAreUsable()
    }

    private fun assertVideoNoteViewerControlsAreUsable() {
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

    private fun setVideoNoteViewerContent() {
        val videoUri = emptyVideoFileUri()
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                LogDateTheme(dynamicColor = false) {
                    VideoNoteViewerContent(
                        mediaRef = videoUri,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                    )
                }
            }
        }
    }

    private fun emptyVideoFileUri(): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "note-viewer-video.mp4")
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
