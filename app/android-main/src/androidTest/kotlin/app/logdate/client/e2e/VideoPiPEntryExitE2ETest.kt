package app.logdate.client.e2e

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.feature.editor.ui.video.VideoPlayerContent
import app.logdate.feature.editor.ui.video.VideoPlayerTags
import app.logdate.ui.theme.LogDateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Instrumented E2E coverage for entering and leaving Picture-in-Picture from the video player.
 *
 * Tapping the PiP affordance should move the host activity into PiP while playback survives, and
 * bringing the host back to the foreground should restore the player rather than a torn-down shell.
 *
 * **Nothing here asks Compose anything once the activity has entered PiP.** Entering PiP detaches
 * the Compose hierarchy from instrumentation, and the binding does not come back when PiP is left
 * — measured on a Pixel 9 Pro API 36 managed device, where a semantics query after the round trip
 * still fails with "no compose hierarchies found" even though the activity has demonstrably
 * returned to full screen. That is what previously made this whole suite unrunnable rather than
 * merely failing.
 *
 * So the Compose assertions all happen before the PiP tap, and everything after it is asserted
 * against the activity, which stays perfectly observable throughout: that it entered PiP, that it
 * was neither finished nor destroyed while there, and that it left PiP again. Whether the player
 * *renders* correctly afterwards is left to the screenshot suite, which can see it.
 *
 * The host (like the production activities) does not override `onPictureInPictureModeChanged`, so
 * this asserts observable PiP state and surface survival rather than callback behaviour.
 */
@RunWith(AndroidJUnit4::class)
class VideoPiPEntryExitE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<VideoPlaybackHostActivity>()

    @Test
    fun `tapping the pip affordance moves the activity into picture in picture`() {
        showVideoPlayer()

        composeRule.onNodeWithTag(VideoPlayerTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoPlayerTags.PLAYER_VIEW).assertIsDisplayed()
        composeRule.onNodeWithTag(VideoPlayerTags.PIP_BUTTON).assertIsDisplayed().performClick()

        assertTrue(
            pollUntil { isHostInPictureInPictureMode() },
            "Tapping the PiP affordance should move the host into Picture-in-Picture",
        )
    }

    /**
     * Playback must not be torn down by the transition: the host stays alive in PiP and comes back
     * out of it, rather than being finished and rebuilt.
     */
    @Test
    fun `the activity survives picture in picture and leaves it again`() {
        showVideoPlayer()

        composeRule.onNodeWithTag(VideoPlayerTags.PIP_BUTTON).assertIsDisplayed().performClick()
        assertTrue(pollUntil { isHostInPictureInPictureMode() }, "Host should have entered PiP")

        assertFalse(isHostGone(), "The host must stay alive in PiP rather than being torn down")

        returnHostToForeground()

        assertTrue(pollUntil { !isHostInPictureInPictureMode() }, "Host should have left PiP")
        assertFalse(isHostGone(), "The host must survive the round trip out of PiP")
    }

    @Test
    fun `the host survives picture in picture followed by home`() {
        showVideoPlayer()

        composeRule.onNodeWithTag(VideoPlayerTags.PIP_BUTTON).assertIsDisplayed().performClick()
        assertTrue(pollUntil { isHostInPictureInPictureMode() }, "Host should have entered PiP")

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome()

        returnHostToForeground()

        assertTrue(pollUntil { !isHostInPictureInPictureMode() }, "Host should have left PiP")
        assertFalse(isHostGone(), "The host must survive PiP and a trip through Home")
    }

    /** Whether the host has been finished or destroyed, read off the main thread. */
    private fun isHostGone(): Boolean = runOnMain { composeRule.activity.isFinishing || composeRule.activity.isDestroyed }

    /**
     * Brings the *same* host back to the front rather than starting a fresh one.
     *
     * Reordering the existing task keeps this instance and its composition alive, so a player that
     * is still there afterwards proves the content survived rather than being rebuilt.
     */
    private fun returnHostToForeground() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.startActivity(
            Intent(context, VideoPlaybackHostActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    /**
     * Polls [condition] on a plain clock rather than through Compose.
     *
     * `ComposeTestRule.waitUntil` drives the Compose clock and needs a hierarchy to attach to, so
     * it cannot be used across a PiP transition — the very thing these tests wait for.
     */
    private fun pollUntil(condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + PIP_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return condition()
    }

    /**
     * Reads the host's PiP state on the main thread, since window and PiP state must not be queried
     * off the UI thread.
     */
    private fun isHostInPictureInPictureMode(): Boolean = runOnMain { composeRule.activity.isInPictureInPictureMode }

    private fun <T> runOnMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return checkNotNull(result) { "runOnMainSync did not run the block" }.getOrThrow()
    }

    private fun showVideoPlayer() {
        val videoUri = emptyVideoFileUri()
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                LogDateTheme(dynamicColor = false) {
                    VideoPlayerContent(uri = videoUri)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun emptyVideoFileUri(): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "pip-entry-exit-check.mp4")
        if (!file.exists()) {
            file.writeBytes(ByteArray(0))
        }
        return Uri.fromFile(file).toString()
    }

    private companion object {
        const val PIP_TIMEOUT_MILLIS = 15_000L
        const val POLL_INTERVAL_MILLIS = 250L
    }
}
