package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.flow.FlowActions
import app.logdate.wear.v2.flow.FlowPage
import app.logdate.wear.v2.flow.FlowUiState
import app.logdate.wear.v2.playback.PlaybackFailure
import app.logdate.wear.v2.playback.PlaybackState
import app.logdate.wear.v2.theme.LogDateWearTheme
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** Every state of Home's moment list, on the graphite theme. */
class FlowPageScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S01_FlowLoading() {
        FlowScene(FlowUiState.Loading)
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S02_FlowEmpty() {
        FlowScene(FlowUiState.Ready(timeZone = flowZone, moments = emptyList()))
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S03_FlowMoments() {
        FlowScene(FlowUiState.Ready(timeZone = flowZone, moments = recentMoments), actions = allFlowActions)
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @Composable
    fun S04_FlowFailed() {
        FlowScene(FlowUiState.Failed)
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S05_FlowPlayingWithEarlier() {
        val playing = recentMoments.first() as JournalNote.Audio
        FlowScene(
            FlowUiState.Ready(timeZone = flowZone, moments = recentMoments, earlierCount = 5),
            playback = PlaybackState.Active(playing.uid, positionMs = 21_000L, durationMs = 42_000L, isPaused = false),
            actions = allFlowActions,
            firstItem = AFTER_ACTIONS_ITEM,
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S06_FlowEarlierOnly() {
        FlowScene(
            FlowUiState.Ready(timeZone = flowZone, moments = emptyList(), earlierCount = 2),
            actions = allFlowActions,
            firstItem = AFTER_ACTIONS_ITEM,
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S07_FlowRowPlayFailed() {
        val failed = recentMoments.first() as JournalNote.Audio
        FlowScene(
            FlowUiState.Ready(timeZone = flowZone, moments = recentMoments, earlierCount = 0),
            playback = PlaybackState.Failed(failed.uid, PlaybackFailure.NOT_ON_WATCH),
            actions = allFlowActions,
            firstItem = AFTER_ACTIONS_ITEM,
        )
    }
}

private val flowZone = TimeZone.currentSystemDefault()

private fun moment(
    day: Int,
    hour: Int,
): Instant = LocalDateTime(2026, 10, day, hour, 15).toInstant(flowZone)

private val recentMoments =
    listOf(
        JournalNote.Audio(mediaRef = "audio/walk.m4a", durationMs = 42_000, creationTimestamp = moment(3, 16), lastUpdated = moment(3, 16)),
        JournalNote.Text(creationTimestamp = moment(3, 9), lastUpdated = moment(3, 9), content = "Coffee before anyone else was up"),
        JournalNote.Image(mediaRef = "photos/lake.jpg", creationTimestamp = moment(2, 18), lastUpdated = moment(2, 18)),
        JournalNote.Text(creationTimestamp = moment(1, 21), lastUpdated = moment(1, 21), content = "Long call with Mum about the trip"),
    )

@Composable
private fun FlowScene(
    state: FlowUiState,
    playback: PlaybackState = PlaybackState.Idle,
    actions: FlowActions = FlowActions(),
    firstItem: Int = 0,
) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = { TimeText(timeSource = FlowTimeSource) }) {
            FlowPage(
                state = state,
                listState = rememberTransformingLazyColumnState(initialAnchorItemIndex = firstItem),
                timeSource = FlowTimeSource,
                actions = actions,
                playback = playback,
            )
        }
    }
}

/** The first item after the list's header and the Memories and Settings buttons: the first moment's row, or the empty message. */
private const val AFTER_ACTIONS_ITEM = 3

/** Every list action set, so the scenes that need them show play buttons, Earlier, Memories and Settings. */
private val allFlowActions =
    FlowActions(onOpenMoment = {}, onPlayPause = {}, onOpenArchive = {}, onOpenMemories = {}, onOpenSettings = {})

/** A fixed clock reading, so the time text renders the same on every run. */
private object FlowTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
