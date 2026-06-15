package app.logdate.client.e2e

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.feature.editor.ui.video.VideoPlayerContent
import app.logdate.feature.editor.ui.video.VideoPlayerTags
import app.logdate.ui.theme.LogDateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VideoPlaybackVisibilityE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<VideoPlaybackHostActivity>()

    @Test
    fun videoPlayerPipAffordanceKeepsVideoVisibleWhenLeavingTheAppSurface() {
        val videoUri = emptyVideoFileUri()

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                LogDateTheme(dynamicColor = false) {
                    VideoPlayerContent(uri = videoUri)
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoPlayerTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoPlayerTags.PLAYER_VIEW).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoPlayerTags.PIP_BUTTON).assertIsDisplayed().performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.activity.isInPictureInPictureMode
        }
    }

    @Test
    fun videoPlayerRemainsVisibleWhenAppIsResizedIntoMultiWindow() {
        val videoUri = emptyVideoFileUri()

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                LogDateTheme(dynamicColor = false) {
                    VideoPlayerContent(uri = videoUri)
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VideoPlayerTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoPlayerTags.PLAYER_VIEW).assertIsDisplayed()

        composeRule.runOnUiThread {
            composeRule.activity.startActivity(
                Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT),
            )
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.activity.isInMultiWindowMode
        }
        composeRule.onNodeWithTag(VideoPlayerTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoPlayerTags.PLAYER_VIEW).assertIsDisplayed()
    }

    private fun emptyVideoFileUri(): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "pip-visibility-check.mp4")
        if (!file.exists()) {
            file.writeBytes(ByteArray(0))
        }
        return Uri.fromFile(file).toString()
    }
}
