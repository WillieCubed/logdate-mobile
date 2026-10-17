package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.memories.MemoriesScreen
import app.logdate.wear.v2.memories.MemoryCards
import app.logdate.wear.v2.memories.OnThisDay
import app.logdate.wear.v2.memories.WeeklyRewind
import app.logdate.wear.v2.theme.LogDateWearTheme
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** Every state of the Memories screen, on the graphite theme. */
class MemoriesScreenshots {
    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S01_MemoriesCards() {
        MemoriesScene(
            MemoryCards(
                onThisDay =
                    OnThisDay(
                        LocalDate(2025, 10, 3),
                        yearsAgo = 1,
                        leadMoment = coffeeMoment,
                        momentCount = 3,
                        summary = "A day at the beach",
                    ),
                rewind = WeeklyRewind("A week of quiet mornings", coffeeMoment),
            ),
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S02_MemoriesLongCardsOnThisDay() {
        MemoriesScene(longMemoryCards)
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S03_MemoriesLongCardsRewind() {
        MemoriesScene(longMemoryCards, firstItem = REWIND_ITEM)
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S04_MemoriesOnThisDayVoiceOnly() {
        MemoriesScene(
            MemoryCards(
                onThisDay = OnThisDay(LocalDate(2024, 10, 3), yearsAgo = 2, leadMoment = walkMoment, momentCount = 1, summary = ""),
            ),
        )
    }

    @PreviewTest
    @WearScreenshotPreviewMatrix
    @WearLargeFontPreview
    @Composable
    fun S05_MemoriesEmpty() {
        MemoriesScene(MemoryCards())
    }
}

private fun memoryMoment(
    day: Int,
    hour: Int,
): Instant = LocalDateTime(2026, 10, day, hour, 15).toInstant(TimeZone.UTC)

private val walkMoment =
    JournalNote.Audio(
        mediaRef = "audio/walk.m4a",
        durationMs = 42_000,
        creationTimestamp = memoryMoment(3, 16),
        lastUpdated = memoryMoment(3, 16),
    )

private val coffeeMoment =
    JournalNote.Text(
        creationTimestamp = memoryMoment(3, 9),
        lastUpdated = memoryMoment(3, 9),
        content = "Coffee before anyone else was up",
    )

/** Cards with words long enough to need their line limits. */
private val longMemoryCards =
    MemoryCards(
        onThisDay =
            OnThisDay(
                date = LocalDate(2023, 10, 3),
                yearsAgo = 3,
                leadMoment = coffeeMoment,
                momentCount = 5,
                summary = "Walked the whole lake with Sam and talked about moving, the trip and what to do next spring",
            ),
        rewind = WeeklyRewind("A week of quiet mornings and long evening walks by the water", coffeeMoment),
    )

@Composable
private fun MemoriesScene(
    cards: MemoryCards,
    firstItem: Int = 0,
) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = { TimeText(timeSource = MemoriesTimeSource) }) {
            MemoriesScreen(
                cards = cards,
                onOpenMoment = {},
                listState = rememberTransformingLazyColumnState(initialAnchorItemIndex = firstItem),
                timeSource = MemoriesTimeSource,
            )
        }
    }
}

/** The Rewind card, after the screen's header and the On this day card. */
private const val REWIND_ITEM = 2

/** A fixed clock reading, so the time text renders the same on every run. */
private object MemoriesTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
