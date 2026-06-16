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
import app.logdate.feature.journals.ui.detail.EntryDisplayData
import app.logdate.feature.journals.ui.detail.JournalDetailScreenContent
import app.logdate.feature.journals.ui.detail.JournalDetailUiState
import app.logdate.ui.audio.AudioPlaybackDisplayInfo
import app.logdate.ui.audio.AudioPlaybackState
import app.logdate.ui.audio.LocalAudioPlaybackState
import app.logdate.ui.media.MediaDeviceSelectorTags
import app.logdate.ui.theme.LogDateTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class JournalInlineAudioE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val journalId = Uuid.parse("00000000-0000-0000-0000-000000009270")
    private val audioEntryId = Uuid.parse("00000000-0000-0000-0000-000000009271")

    @Test
    fun compactJournalAudioCardExposesOutputRouteControls() {
        assumeTrue(screenWidthDp() < 600)
        setJournalDetailContent()

        composeRule.waitForIdle()
        assertOutputRouteControlsAreUsable()
    }

    @Test
    fun expandedJournalAudioCardExposesOutputRouteControls() {
        assumeTrue(screenWidthDp() >= 600)
        setJournalDetailContent()

        composeRule.waitForIdle()
        assertOutputRouteControlsAreUsable()
    }

    private fun assertOutputRouteControlsAreUsable() {
        composeRule.onNodeWithTag("journal-audio-playback-button").assertIsDisplayed()
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

    private fun setJournalDetailContent() {
        composeRule.setContent {
            LogDateTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalAudioPlaybackState provides playbackState()) {
                    JournalDetailScreenContent(
                        uiState =
                            JournalDetailUiState.Success(
                                journalId = journalId,
                                title = "Field notes",
                                entries =
                                    listOf(
                                        EntryDisplayData.AudioEntry(
                                            id = audioEntryId,
                                            timestamp = Clock.System.now(),
                                            mediaRef = "content://logdate/audio/journal-inline.m4a",
                                            durationMs = 108_000L,
                                            locationName = "Studio",
                                        ),
                                    ),
                            ),
                        onGoBack = {},
                    )
                }
            }
        }
    }

    private fun playbackState(): AudioPlaybackState =
        AudioPlaybackState(
            currentlyPlayingId = audioEntryId,
            currentUri = "content://logdate/audio/journal-inline.m4a",
            isPlaying = true,
            progress = 0.42f,
            duration = 2.minutes,
            displayInfo =
                AudioPlaybackDisplayInfo(
                    title = "Journal voice note",
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
