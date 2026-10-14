package app.logdate.wear.screenshots.audit

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.playback.AudioOutputState
import app.logdate.wear.presentation.theme.LogDateTheme
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

class WearDayDetailAudioScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun OutputRoutePickerClosed() {
        LogDateTheme {
            AppScaffold(timeText = { TimeText(timeSource = PreviewTimeSource) }) {
                WearDayDetailContent(
                    detail = wearDayDetail,
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.SpeakerOnly,
                    onToggleNote = {},
                    onChooseOutput = {},
                    timeSource = PreviewTimeSource,
                )
            }
        }
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun OutputRoutePickerSystemControlled() {
        LogDateTheme {
            AppScaffold(timeText = { TimeText(timeSource = PreviewTimeSource) }) {
                WearDayDetailContent(
                    detail = wearDayDetail,
                    playbackState = WearPlaybackUiState.Idle,
                    audioOutputState = AudioOutputState.Unavailable,
                    onToggleNote = {},
                    onChooseOutput = {},
                    timeSource = PreviewTimeSource,
                )
            }
        }
    }
}

private object PreviewTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
