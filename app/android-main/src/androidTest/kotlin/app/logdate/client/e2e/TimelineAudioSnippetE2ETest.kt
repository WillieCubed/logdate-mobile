package app.logdate.client.e2e

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
import app.logdate.feature.timeline.ui.details.AudioNoteSnippet
import app.logdate.ui.audio.AudioPlaybackDisplayInfo
import app.logdate.ui.audio.AudioPlaybackState
import app.logdate.ui.audio.LocalAudioPlaybackState
import app.logdate.ui.media.MediaDeviceSelectorTags
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.timeline.AudioNoteUiState
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class TimelineAudioSnippetE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val noteId = Uuid.parse("00000000-0000-0000-0000-000000009251")

    @Test
    fun compactTimelineAudioSnippetExposesOutputRouteControls() {
        assumeTrue(screenWidthDp() < 600)
        setTimelineAudioSnippetContent()

        composeRule.waitForIdle()
        assertOutputRouteControlsAreUsable()
    }

    @Test
    fun expandedTimelineAudioSnippetExposesOutputRouteControls() {
        assumeTrue(screenWidthDp() >= 600)
        setTimelineAudioSnippetContent()

        composeRule.waitForIdle()
        assertOutputRouteControlsAreUsable()
    }

    private fun assertOutputRouteControlsAreUsable() {
        composeRule.onNodeWithText("Audio Recording").assertIsDisplayed()
        composeRule.onAllNodesWithTag(MediaDeviceSelectorTags.chip("Audio output"))[0]
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeRule.onNodeWithText("Use Android's output switcher to route playback.").assertIsDisplayed()
        composeRule.onNodeWithText("Open output switcher").assertIsDisplayed()
        composeRule
            .onNodeWithTag(MediaDeviceSelectorTags.systemSettingsButton("Audio output"))
            .assertIsDisplayed()
            .assertHasClickAction()

        pressBack()
    }

    private fun setTimelineAudioSnippetContent() {
        composeRule.setContent {
            LogDateTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalAudioPlaybackState provides playbackState()) {
                    AudioNoteSnippet(
                        uiState =
                            AudioNoteUiState(
                                noteId = noteId,
                                uri = "content://logdate/audio/timeline-note.m4a",
                                timestamp = Clock.System.now(),
                                duration = 108_000L,
                            ),
                    )
                }
            }
        }
    }

    private fun playbackState(): AudioPlaybackState =
        AudioPlaybackState(
            currentlyPlayingId = noteId,
            currentUri = "content://logdate/audio/timeline-note.m4a",
            isPlaying = true,
            progress = 0.42f,
            duration = 2.minutes,
            displayInfo =
                AudioPlaybackDisplayInfo(
                    title = "Timeline voice note",
                    subtitle = "1:48",
                ),
            outputSelection = systemControlledOutputSelection(),
        )

    private fun systemControlledOutputSelection(): MediaDeviceSelectionUiState =
        MediaDeviceSelectionUiState(
            kind = MediaDeviceKind.AUDIO_OUTPUT,
            devices =
                listOf(
                    MediaDeviceUiState(
                        id = "speaker",
                        label = "Phone speaker",
                        kind = MediaDeviceKind.AUDIO_OUTPUT,
                        category = MediaDeviceCategory.BUILT_IN,
                    ),
                    MediaDeviceUiState(
                        id = "bluetooth",
                        label = "Bluetooth headphones",
                        kind = MediaDeviceKind.AUDIO_OUTPUT,
                        category = MediaDeviceCategory.BLUETOOTH,
                        isExternal = true,
                    ),
                ),
            selectedDeviceId = "speaker",
            isSelectionControllable = false,
            routeControlMessage = "Use Android's output switcher to route playback.",
        )

    private fun screenWidthDp(): Int =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .resources
            .configuration
            .screenWidthDp
}
