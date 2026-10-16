@file:Suppress("ktlint:standard:function-naming")

package app.logdate.screenshots.shared

import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.media.MediaObject
import app.logdate.client.repository.search.SearchContentType
import app.logdate.client.repository.search.SearchResult
import app.logdate.feature.rewind.ui.BasicTextRewindPanelUiState
import app.logdate.feature.rewind.ui.HighlightedQuoteRewindPanelUiState
import app.logdate.feature.rewind.ui.NarrativeContextRewindPanelUiState
import app.logdate.feature.rewind.ui.SubtitledRewindPanelUiState
import app.logdate.feature.rewind.ui.overview.RewindHistoryUiState
import app.logdate.feature.rewind.ui.overview.RewindPreviewUiState
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal const val SEARCH_QUERY = "sun"

internal val baseInstant = Instant.fromEpochMilliseconds(1_740_000_000_000L)

internal val rewindPreview =
    RewindPreviewUiState(
        message = "You packed this week with movement, memories, and a lot of camera roll receipts.",
        rewindId = Uuid.parse("11111111-2222-3333-4444-555555555555"),
        label = "This Week",
        title = "Five cities in seven days",
        start = LocalDate(2026, 4, 6),
        end = LocalDate(2026, 4, 12),
        rewindAvailable = true,
        isViewed = false,
        entryCount = 9,
        photoCount = 41,
        peopleCount = 6,
        primaryLocation = "San Francisco",
    )

internal val pastRewindSamples =
    listOf(
        RewindHistoryUiState(
            uid = Uuid.parse("22222222-3333-4444-5555-666666666666"),
            title = "A quieter week at home",
            label = "Week of Mar 30",
            startDate = LocalDate(2026, 3, 30),
            endDate = LocalDate(2026, 4, 5),
            message = "Rain three days running, three book chapters, and one really good chili.",
            isViewed = true,
            entryCount = 6,
            photoCount = 12,
            peopleCount = 2,
            primaryLocation = "Brooklyn",
        ),
        RewindHistoryUiState(
            uid = Uuid.parse("33333333-4444-5555-6666-777777777777"),
            title = "The trip that almost wasn't",
            label = "Week of Mar 23",
            startDate = LocalDate(2026, 3, 23),
            endDate = LocalDate(2026, 3, 29),
            message = "Late flight, new city, and the kind of dinner you talk about for months.",
            isViewed = true,
            entryCount = 11,
            photoCount = 53,
            peopleCount = 4,
            primaryLocation = "Lisbon",
        ),
        RewindHistoryUiState(
            uid = Uuid.parse("44444444-5555-6666-7777-888888888888"),
            title = "Heads-down week",
            label = "Week of Mar 16",
            startDate = LocalDate(2026, 3, 16),
            endDate = LocalDate(2026, 3, 22),
            message = "You shipped the thing on Friday and slept eleven hours on Saturday.",
            isViewed = true,
            entryCount = 4,
            photoCount = 7,
            peopleCount = 1,
            primaryLocation = "Brooklyn",
        ),
    )

internal val rewindDetailPanels =
    listOf(
        SubtitledRewindPanelUiState(
            title = "Five cities in seven days",
            subtitle = "April 6 — April 12, 2026",
        ),
        NarrativeContextRewindPanelUiState(
            sourceId = Uuid.parse("aaaaaaaa-1111-2222-3333-444444444444"),
            timestamp = baseInstant,
            contextText = "You started in San Francisco and chased one open afternoon at a time across the coast.",
        ),
        HighlightedQuoteRewindPanelUiState(
            text = "I forgot how loud the ocean is when nothing else is.",
            whyItHits = "You came back to silence twice this week.",
        ),
        BasicTextRewindPanelUiState(
            text = "That was your week.",
        ),
    )

internal val searchResults =
    listOf(
        SearchResult(
            uid = Uuid.parse("00000000-0000-0000-0000-000000000061"),
            content = "Caught the sunrise train and wrote down the quiet before the city woke up.",
            created = baseInstant,
            contentType = SearchContentType.TEXT_NOTE,
        ),
        SearchResult(
            uid = Uuid.parse("00000000-0000-0000-0000-000000000062"),
            content = "Voice memo on the sunrise trail, the route changes, and what still needs shipping.",
            created = baseInstant,
            contentType = SearchContentType.TRANSCRIPTION,
        ),
    )

internal fun sampleLocationSettings(): LocationTrackingSettings =
    LocationTrackingSettings(
        backgroundTrackingEnabled = true,
        minimumPersistIntervalMinutes = 15,
        captureMode = LocationCaptureMode.PASSIVE,
        serverAssistEnabled = true,
        autoTrackForJournalEntries = true,
        autoTrackForTimelineReview = false,
    )

internal fun sampleMemories(
    videoEvery: Int = 3,
    videoDuration: Duration = Duration.parse("30s"),
): List<MediaObject> =
    (1..12).map { index ->
        val timestamp = Instant.fromEpochMilliseconds(baseInstant.toEpochMilliseconds() + (index * 60_000))
        if (index % videoEvery == 0) {
            MediaObject.Video(
                uri = "sample$index",
                size = 2048,
                name = "VID_$index.mp4",
                timestamp = timestamp,
                duration = videoDuration,
            )
        } else {
            MediaObject.Image(
                uri = "sample$index",
                size = 1024,
                name = "IMG_$index.jpg",
                timestamp = timestamp,
            )
        }
    }
