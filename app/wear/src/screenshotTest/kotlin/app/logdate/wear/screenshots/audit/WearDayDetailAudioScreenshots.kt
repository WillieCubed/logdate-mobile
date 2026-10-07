package app.logdate.wear.screenshots.audit

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme
import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.playback.AudioOutputState
import app.logdate.wear.presentation.timeline.WearDayDetailContent
import app.logdate.wear.presentation.timeline.WearDayDetailUiState
import app.logdate.wear.presentation.timeline.WearPlaybackUiState
import app.logdate.wear.screenshots.WearScreenshotPreviewMatrix
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.uuid.Uuid

private val previewNoteTime = LocalDateTime(2024, 3, 9, 13, 20).toInstant(TimeZone.currentSystemDefault())

private val wearDayDetail =
    WearDayDetailUiState(
        date = LocalDate(2024, 3, 9),
        entries =
            listOf(
                JournalNote.Audio(
                    uid = Uuid.parse("00000000-0000-0000-0000-000000000031"),
                    creationTimestamp = previewNoteTime,
                    lastUpdated = previewNoteTime,
                    mediaRef = "preview://wear/day-detail/audio",
                    durationMs = 15_000,
                ),
            ),
    )

private val controllableOutputSelection =
    MediaDeviceSelectionUiState(
        kind = MediaDeviceKind.AUDIO_OUTPUT,
        devices =
            listOf(
                MediaDeviceUiState(
                    id = "speaker",
                    label = "Watch speaker",
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
                    label = "Watch speaker",
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
        routeControlMessage = "Use the watch or phone system output controls to route playback.",
    )

class WearDayDetailAudioScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun OutputRoutePickerClosed() {
        MaterialTheme {
            WearDayDetailContent(
                detail = wearDayDetail,
                playbackState = WearPlaybackUiState.Idle,
                audioOutputState = AudioOutputState.SpeakerOnly,
                outputSelection = controllableOutputSelection,
                isOutputPickerVisible = false,
                onToggleOutputPicker = {},
                onSelectOutputDevice = {},
                onToggleNote = {},
                onOpenBluetoothSettings = {},
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun OutputRoutePickerOpen() {
        MaterialTheme {
            WearDayDetailContent(
                detail = wearDayDetail,
                playbackState = WearPlaybackUiState.Idle,
                audioOutputState = AudioOutputState.SpeakerAndBluetooth,
                outputSelection = controllableOutputSelection,
                isOutputPickerVisible = true,
                onToggleOutputPicker = {},
                onSelectOutputDevice = {},
                onToggleNote = {},
                onOpenBluetoothSettings = {},
            )
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun OutputRoutePickerSystemControlled() {
        MaterialTheme {
            WearDayDetailContent(
                detail = wearDayDetail,
                playbackState = WearPlaybackUiState.Idle,
                audioOutputState = AudioOutputState.Unavailable,
                outputSelection = systemControlledOutputSelection,
                isOutputPickerVisible = true,
                onToggleOutputPicker = {},
                onSelectOutputDevice = {},
                onToggleNote = {},
                onOpenBluetoothSettings = {},
            )
        }
    }
}
