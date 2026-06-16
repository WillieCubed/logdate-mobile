package app.logdate.screenshots.audit.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
import app.logdate.feature.editor.audio.model.AudioPalette
import app.logdate.feature.editor.ui.audio.expansion.ImmersiveAudioScreen
import app.logdate.feature.journals.ui.detail.EntryDisplayData
import app.logdate.feature.journals.ui.detail.JournalDetailScreenContent
import app.logdate.feature.journals.ui.detail.JournalDetailUiState
import app.logdate.feature.timeline.ui.details.AudioNoteSnippet
import app.logdate.screenshots.common.LargeScreenAuditPreviewMatrix
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.ui.audio.AudioPlaybackDisplayInfo
import app.logdate.ui.audio.AudioPlaybackState
import app.logdate.ui.audio.LocalAudioPlaybackState
import app.logdate.ui.audio.MiniAudioPlayer
import app.logdate.ui.timeline.AudioNoteUiState
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private val controllableOutputSelection =
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
    )

private val systemControlledOutputSelection =
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

private val playbackState =
    AudioPlaybackState(
        currentlyPlayingId = Uuid.parse("00000000-0000-0000-0000-000000000171"),
        currentUri = "preview://audio/playback",
        isPlaying = true,
        progress = 0.42f,
        duration = 2.minutes,
        displayInfo =
            AudioPlaybackDisplayInfo(
                title = "Voice note",
                subtitle = "2:00",
                accentColor = 0xFFE8A044,
            ),
        outputSelection = controllableOutputSelection,
    )

private val systemControlledPlaybackState = playbackState.copy(
    outputSelection = systemControlledOutputSelection,
)

private val inlineAudioNoteId = Uuid.parse("00000000-0000-0000-0000-000000000172")

private val inlinePlaybackState =
    playbackState.copy(
        currentlyPlayingId = inlineAudioNoteId,
        currentUri = "preview://audio/inline",
        displayInfo =
            AudioPlaybackDisplayInfo(
                title = "Audio memory",
                subtitle = "1:48",
                accentColor = 0xFFE8A044,
            ),
    )

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun A12_MiniAudioPlayerWithRouteSelector() {
    ScreenshotTheme {
        CompositionLocalProvider(LocalAudioPlaybackState provides playbackState) {
            MiniAudioPlayer(onOpenFullPlayer = {})
        }
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A13_ImmersiveAudioWithRouteSelector() {
    ScreenshotTheme {
        ImmersiveAudioScreen(
            amplitudes = ScreenshotTestData.mockAmplitudes,
            progress = 0.42f,
            isPlaying = true,
            palette =
                AudioPalette(
                    waveformGradientStart = 0xFFE8A044,
                    waveformGradientEnd = 0xFFD4603A,
                    playedFillColor = 0xFFE8A044,
                    accentColor = 0xFFE8A044,
                    immersiveBackground = 0xFF1A0F05,
                ),
            daylightPeriod = app.logdate.client.awareness.daylight.DaylightPeriod.GOLDEN_HOUR,
            durationMs = 120_000L,
            createdAt = ScreenshotTestData.baseInstant,
            outputSelection = controllableOutputSelection,
            onOutputDeviceSelected = {},
        )
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A14_ImmersiveAudioSystemControlledRoute() {
    ScreenshotTheme {
        ImmersiveAudioScreen(
            amplitudes = ScreenshotTestData.mockAmplitudes,
            progress = 0.42f,
            isPlaying = true,
            palette =
                AudioPalette(
                    waveformGradientStart = 0xFFE8A044,
                    waveformGradientEnd = 0xFFD4603A,
                    playedFillColor = 0xFFE8A044,
                    accentColor = 0xFFE8A044,
                    immersiveBackground = 0xFF1A0F05,
                ),
            daylightPeriod = app.logdate.client.awareness.daylight.DaylightPeriod.GOLDEN_HOUR,
            durationMs = 120_000L,
            createdAt = ScreenshotTestData.baseInstant,
            outputSelection = systemControlledPlaybackState.outputSelection,
            onOutputDeviceSelected = {},
        )
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun A15_JournalInlineAudioRouteSelector() {
    ScreenshotTheme {
        CompositionLocalProvider(LocalAudioPlaybackState provides inlinePlaybackState) {
            JournalDetailScreenContent(
                uiState =
                    JournalDetailUiState.Success(
                        journalId = Uuid.parse("00000000-0000-0000-0000-000000000273"),
                        title = "Weekend notes",
                        entries =
                            listOf(
                                EntryDisplayData.AudioEntry(
                                    id = inlineAudioNoteId,
                                    timestamp = ScreenshotTestData.baseInstant,
                                    mediaRef = "preview://audio/journal-inline",
                                    durationMs = 108_000L,
                                    locationName = "Kitchen table",
                                ),
                            ),
                    ),
                onGoBack = {},
            )
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun A16_TimelineAudioSnippetRouteSelector() {
    ScreenshotTheme {
        CompositionLocalProvider(LocalAudioPlaybackState provides inlinePlaybackState) {
            AudioNoteSnippet(
                uiState =
                    AudioNoteUiState(
                        noteId = inlineAudioNoteId,
                        uri = "preview://audio/timeline-snippet",
                        timestamp = ScreenshotTestData.baseInstant,
                        duration = 108_000L,
                    ),
            )
        }
    }
}
