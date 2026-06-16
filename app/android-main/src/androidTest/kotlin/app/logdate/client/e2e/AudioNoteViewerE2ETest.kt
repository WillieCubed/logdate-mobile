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
import app.logdate.client.awareness.daylight.DaylightPeriod
import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
import app.logdate.feature.editor.audio.AudioContext
import app.logdate.feature.editor.audio.model.AudioPalette
import app.logdate.feature.editor.audio.model.AudioSegment
import app.logdate.feature.editor.audio.model.SegmentType
import app.logdate.feature.journals.ui.detail.AudioNoteViewerContent
import app.logdate.feature.journals.ui.detail.AudioNoteViewerUiState
import app.logdate.feature.journals.ui.detail.AudioPlaybackUiState
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class AudioNoteViewerE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val noteId = Uuid.parse("00000000-0000-0000-0000-000000009291")

    @Test
    fun compactAudioNoteViewerExposesOutputRouteControls() {
        assumeTrue(screenWidthDp() < 600)
        setAudioViewerContent()

        composeRule.waitForIdle()
        assertOutputRouteControlsAreUsable()
    }

    @Test
    fun expandedAudioNoteViewerExposesOutputRouteControls() {
        assumeTrue(screenWidthDp() >= 600)
        setAudioViewerContent()

        composeRule.waitForIdle()
        assertOutputRouteControlsAreUsable()
    }

    private fun assertOutputRouteControlsAreUsable() {
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

    private fun setAudioViewerContent() {
        composeRule.setContent {
            LogDateTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalAudioPlaybackState provides playbackState()) {
                    AudioNoteViewerContent(
                        uiState =
                            AudioNoteViewerUiState.Ready(
                                mediaRef = "content://logdate/audio/viewer.m4a",
                                durationMs = 182_000L,
                                createdAt = Clock.System.now(),
                                context = audioContext(),
                                playbackState = AudioPlaybackUiState(progress = 0.38f, isPlaying = true),
                            ),
                        onGoBack = {},
                    )
                }
            }
        }
    }

    private fun audioContext(): AudioContext =
        AudioContext(
            amplitudes = List(48) { index -> ((index % 9) + 1) / 10f },
            segments =
                listOf(
                    AudioSegment(timestampMs = 8_000L, type = SegmentType.SPEECH_ONSET),
                    AudioSegment(timestampMs = 34_000L, type = SegmentType.VOLUME_PEAK),
                ),
            daylightPeriod = DaylightPeriod.GOLDEN_HOUR,
            palette =
                AudioPalette(
                    waveformGradientStart = 0xFFE8A044,
                    waveformGradientEnd = 0xFFD4603A,
                    playedFillColor = 0xFFE8A044,
                    accentColor = 0xFFE8A044,
                    immersiveBackground = 0xFF1A0F05,
                ),
        )

    private fun playbackState(): AudioPlaybackState =
        AudioPlaybackState(
            currentlyPlayingId = noteId,
            currentUri = "content://logdate/audio/viewer.m4a",
            isPlaying = true,
            progress = 0.38f,
            duration = 182_000L.milliseconds,
            displayInfo =
                AudioPlaybackDisplayInfo(
                    title = "Audio note",
                    subtitle = "3:02",
                    accentColor = 0xFFE8A044,
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
