package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.lazy.TransformationSpec
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.flow.FlowActions
import app.logdate.wear.v2.flow.FlowPage
import app.logdate.wear.v2.flow.FlowUiState
import app.logdate.wear.v2.memories.MemoryCards
import app.logdate.wear.v2.memories.OnThisDay
import app.logdate.wear.v2.memories.WeeklyRewind
import app.logdate.wear.v2.memories.memoryCardItems
import app.logdate.wear.v2.playback.PlaybackFailure
import app.logdate.wear.v2.playback.PlaybackState
import app.logdate.wear.v2.theme.LogDateWearTheme
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDate
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
        FlowScene(FlowUiState.Ready(timeZone = flowZone, moments = recentMoments))
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
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S06_FlowEarlierOnly() {
        FlowScene(FlowUiState.Ready(timeZone = flowZone, moments = emptyList(), earlierCount = 2), actions = allFlowActions)
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
            firstItem = FIRST_MOMENT_ITEM,
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S08_FlowMemories() {
        val lastYear = recentMoments[1]
        val cards =
            MemoryCards(
                onThisDay =
                    OnThisDay(
                        LocalDate(2025, 10, 3),
                        yearsAgo = 1,
                        leadMoment = lastYear,
                        momentCount = 3,
                        summary = "A day at the beach",
                    ),
                rewind = WeeklyRewind("A week of quiet mornings", lastYear),
            )
        FlowScene(
            FlowUiState.Ready(timeZone = flowZone, moments = emptyList(), earlierCount = 0),
            actions = allFlowActions,
            cards = { spec -> memoryCardItems(cards, spec, onOpenMoment = {}) },
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S09_FlowMemoriesAfterMoments() {
        FlowScene(
            FlowUiState.Ready(timeZone = flowZone, moments = recentMoments, earlierCount = 3),
            actions = allFlowActions,
            cards = { spec -> memoryCardItems(longMemoryCards, spec, onOpenMoment = {}) },
            firstItem = ON_THIS_DAY_ITEM,
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S11_FlowRewindBeforeEarlier() {
        FlowScene(
            FlowUiState.Ready(timeZone = flowZone, moments = recentMoments, earlierCount = 3),
            actions = allFlowActions,
            cards = { spec -> memoryCardItems(longMemoryCards, spec, onOpenMoment = {}) },
            firstItem = REWIND_ITEM,
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S10_FlowOnThisDayVoiceOnly() {
        val walk = recentMoments.first()
        val cards =
            MemoryCards(onThisDay = OnThisDay(LocalDate(2024, 10, 3), yearsAgo = 2, leadMoment = walk, momentCount = 1, summary = ""))
        FlowScene(
            FlowUiState.Ready(timeZone = flowZone, moments = emptyList(), earlierCount = 0),
            actions = allFlowActions,
            cards = { spec -> memoryCardItems(cards, spec, onOpenMoment = {}) },
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
    cards: TransformingLazyColumnScope.(TransformationSpec) -> Unit = {},
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
                cards = cards,
            )
        }
    }
}

/** The first moment's row, after the list's header. */
private const val FIRST_MOMENT_ITEM = 1

/** The On this day card: after the list's header and the four moments of [recentMoments]. */
private const val ON_THIS_DAY_ITEM = 5

/** The Rewind card, right after the On this day card. */
private const val REWIND_ITEM = 6

/** Cards with words long enough to need their line limits. */
private val longMemoryCards =
    MemoryCards(
        onThisDay =
            OnThisDay(
                date = LocalDate(2023, 10, 3),
                yearsAgo = 3,
                leadMoment = recentMoments[1],
                momentCount = 5,
                summary = "Walked the whole lake with Sam and talked about moving, the trip and what to do next spring",
            ),
        rewind = WeeklyRewind("A week of quiet mornings and long evening walks by the water", recentMoments[1]),
    )

/** Every list action set, so the scenes that need them show play buttons and Earlier. */
private val allFlowActions = FlowActions(onOpenMoment = {}, onPlayPause = {}, onOpenArchive = {})

/** A fixed clock reading, so the time text renders the same on every run. */
private object FlowTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
