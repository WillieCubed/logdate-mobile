package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.archive.ArchiveDay
import app.logdate.wear.v2.archive.ArchiveScreen
import app.logdate.wear.v2.archive.ArchiveUiState
import app.logdate.wear.v2.playback.PlaybackState
import app.logdate.wear.v2.theme.LogDateWearTheme
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** Every state of the Archive, on the graphite theme. */
class ArchiveScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S01_ArchiveDays() {
        ArchiveScene(ArchiveUiState.Ready(archiveZone, archiveDays))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S02_ArchiveEmpty() {
        ArchiveScene(ArchiveUiState.Ready(archiveZone, emptyList()))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S03_ArchiveFailed() {
        ArchiveScene(ArchiveUiState.Failed)
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S04_ArchiveLoading() {
        ArchiveScene(ArchiveUiState.Loading)
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S05_ArchiveScrolledToPlayingRow() {
        val voice = archiveDays.last().moments.last() as JournalNote.Audio
        ArchiveScene(
            ArchiveUiState.Ready(archiveZone, archiveDays),
            playback = PlaybackState.Active(voice.uid, positionMs = 30_000L, durationMs = 95_000L, isPaused = false),
            firstItem = ARCHIVE_VOICE_ROW_ITEM,
        )
    }
}

private val archiveZone = TimeZone.currentSystemDefault()

private fun archiveTime(
    day: Int,
    hour: Int,
): Instant = LocalDateTime(2026, 9, day, hour, 15).toInstant(archiveZone)

private val archiveDays =
    listOf(
        ArchiveDay(
            LocalDate(2026, 9, 24),
            listOf(
                JournalNote.Text(
                    creationTimestamp = archiveTime(24, 21),
                    lastUpdated = archiveTime(24, 21),
                    content = "Long call about the trip",
                ),
            ),
        ),
        ArchiveDay(
            LocalDate(2026, 9, 21),
            listOf(
                JournalNote.Text(
                    creationTimestamp = archiveTime(21, 20),
                    lastUpdated = archiveTime(21, 20),
                    content = "Dinner on the roof",
                ),
                JournalNote.Audio(
                    mediaRef = "audio/walk.m4a",
                    durationMs = 95_000L,
                    creationTimestamp = archiveTime(21, 8),
                    lastUpdated = archiveTime(21, 8),
                ),
            ),
        ),
    )

@Composable
private fun ArchiveScene(
    state: ArchiveUiState,
    playback: PlaybackState = PlaybackState.Idle,
    firstItem: Int = 0,
) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = { TimeText(timeSource = ArchiveTimeSource) }) {
            ArchiveScreen(
                state = state,
                listState = rememberTransformingLazyColumnState(initialAnchorItemIndex = firstItem),
                timeSource = ArchiveTimeSource,
                playback = playback,
                onOpenMoment = {},
                onPlayPause = {},
            )
        }
    }
}

/** The voice note's row: after the title, each day's header and the moments before it. */
private const val ARCHIVE_VOICE_ROW_ITEM = 5

/** A fixed clock reading, so the time text renders the same on every run. */
private object ArchiveTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
