package app.logdate.screenshots.flows.flow02_home_timeline

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import app.logdate.feature.journals.ui.JournalLayoutMode
import app.logdate.feature.journals.ui.JournalListItemUiState
import app.logdate.feature.journals.ui.JournalListPanel
import app.logdate.feature.journals.ui.JournalSortOption
import app.logdate.feature.rewind.ui.RewindScreenContent
import app.logdate.feature.rewind.ui.overview.RewindHistoryUiState
import app.logdate.feature.rewind.ui.overview.RewindOverviewScreenUiState
import app.logdate.feature.rewind.ui.overview.RewindPreviewUiState
import app.logdate.feature.timeline.ui.details.TimelineDayDetailPanel
import app.logdate.feature.timeline.ui.details.TimelineDetailsEmptyPlaceholder
import app.logdate.screenshots.common.HomeDetailRouteFrame
import app.logdate.screenshots.common.HomeTabRouteFrame
import app.logdate.screenshots.common.RoutePreviewTab
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.ImageNoteUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelineLoadingState
import app.logdate.ui.timeline.TimelinePane
import app.logdate.ui.timeline.TimelineSuggestionBlock
import app.logdate.ui.timeline.TimelineUiState
import app.logdate.ui.timeline.VideoNoteUiState
import app.logdate.ui.timeline.createTimelineDayUiState
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

private val timelineDays =
    listOf(
        createTimelineDayUiState(
            summary = "Wrapped up the route inventory and started wiring screenshot helpers.",
            date = LocalDate(2025, 2, 20),
            notes =
                listOf(
                    ImageNoteUiState(
                        noteId = Uuid.parse("00000000-0000-0000-0000-000000000031"),
                        uri = "android.resource://co.reasonabletech.logdate/mipmap/ic_launcher",
                        timestamp = ScreenshotTestData.baseInstant,
                    ),
                    TextNoteUiState(
                        noteId = Uuid.parse("00000000-0000-0000-0000-000000000032"),
                        text = "Captured the route shell, then tightened spacing around the new timeline cards.",
                        timestamp = ScreenshotTestData.baseInstant,
                    ),
                ),
            placesVisited =
                listOf(
                    PlaceUiState(id = "place-1", title = "Blue Bottle Coffee"),
                    PlaceUiState(id = "place-2", title = "Dolores Park"),
                ),
        ),
        createTimelineDayUiState(
            summary = "Captured the golden-hour ferry ride home after a long day.",
            date = LocalDate(2025, 2, 19),
            notes =
                listOf(
                    AudioNoteUiState(
                        noteId = Uuid.parse("00000000-0000-0000-0000-000000000033"),
                        uri = "preview://audio",
                        timestamp = ScreenshotTestData.baseInstant,
                        duration = 67_000L,
                    ),
                    TextNoteUiState(
                        noteId = Uuid.parse("00000000-0000-0000-0000-000000000034"),
                        text = "Kept the end-of-day note short and let the audio carry the texture instead.",
                        timestamp = ScreenshotTestData.baseInstant,
                    ),
                ),
            placesVisited = listOf(PlaceUiState(id = "place-3", title = "Home")),
        ),
    )

private val timelineDetailState =
    TimelineDayUiState(
        summary = "Shipped route-level screenshot coverage and closed the biggest gaps in the Android graph.",
        date = LocalDate(2025, 2, 20),
        notes =
            listOf(
                TextNoteUiState(
                    noteId = Uuid.parse("00000000-0000-0000-0000-000000000041"),
                    text = "The route harness now renders the same adaptive shell as the home scene.",
                    timestamp = ScreenshotTestData.baseInstant,
                ),
                ImageNoteUiState(
                    noteId = Uuid.parse("00000000-0000-0000-0000-000000000042"),
                    uri = "android.resource://co.reasonabletech.logdate/mipmap/ic_launcher",
                    timestamp = ScreenshotTestData.baseInstant,
                ),
                AudioNoteUiState(
                    noteId = Uuid.parse("00000000-0000-0000-0000-000000000043"),
                    uri = "preview://audio",
                    timestamp = ScreenshotTestData.baseInstant,
                    duration = 93_000L,
                ),
                VideoNoteUiState(
                    noteId = Uuid.parse("00000000-0000-0000-0000-000000000044"),
                    uri = "preview://video",
                    timestamp = ScreenshotTestData.baseInstant,
                    thumbnailUri = "android.resource://co.reasonabletech.logdate/mipmap/ic_launcher",
                    duration = 61_000L,
                ),
            ),
        placesVisited =
            listOf(
                PlaceUiState(
                    id = "place-1",
                    title = "Blue Bottle Coffee",
                    latitude = 37.7764,
                    longitude = -122.4231,
                ),
                PlaceUiState(
                    id = "place-2",
                    title = "Dolores Park",
                    latitude = 37.7596,
                    longitude = -122.4269,
                ),
            ),
    )

private val timelineNoPeopleState =
    timelineDetailState.copy(
        people = emptyList(),
        notes = timelineDetailState.notes.take(1),
    )

private val rewindId = Uuid.parse("00000000-0000-0000-0000-000000000051")
private val pastRewinds =
    listOf(
        RewindHistoryUiState(uid = rewindId, title = "Week of Feb 17"),
        RewindHistoryUiState(uid = Uuid.parse("00000000-0000-0000-0000-000000000052"), title = "Week of Feb 10"),
    )

private val mostRecentRewind =
    RewindPreviewUiState(
        message = "Your week in review",
        rewindId = rewindId,
        label = "This Week",
        title = "A Week of Intentional Progress",
        start = LocalDate(2025, 2, 17),
        end = LocalDate(2025, 2, 23),
        rewindAvailable = true,
    )

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S01_TimelineSuggestionCompleteYourDraft() {
    ScreenshotTheme {
        HomeTabRouteFrame(selectedTab = RoutePreviewTab.TIMELINE) {
            TimelinePane(
                uiState = TimelineUiState(items = timelineDays),
                onNewEntry = {},
                onShareMemory = {},
                onOpenDay = {},
                onSearchClick = {},
                onProfileClick = {},
                onHistoryClick = {},
                timelineSuggestion =
                    TimelineSuggestionBlock.CompleteDraft(
                        draftId = "draft-1",
                        notePreview = "You have an unfinished memory from this afternoon.",
                    ),
            )
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S02_TimelineEmpty() {
    ScreenshotTheme {
        HomeTabRouteFrame(selectedTab = RoutePreviewTab.TIMELINE) {
            TimelinePane(
                uiState = TimelineUiState(items = emptyList(), loadingState = TimelineLoadingState.InitialLoading),
                onNewEntry = {},
                onShareMemory = {},
                onOpenDay = {},
                onSearchClick = {},
                onProfileClick = {},
                onHistoryClick = {},
            )
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_TimelineScrollToTopVisible() {
    ScreenshotTheme {
        HomeTabRouteFrame(selectedTab = RoutePreviewTab.TIMELINE) {
            TimelinePane(
                uiState = TimelineUiState(items = List(8) { index -> timelineDays[index % timelineDays.size].copy(date = LocalDate(2025, 2, 20 - index)) }),
                onNewEntry = {},
                onShareMemory = {},
                onOpenDay = {},
                onSearchClick = {},
                onProfileClick = {},
                onHistoryClick = {},
                listState = LazyListState(firstVisibleItemIndex = 2, firstVisibleItemScrollOffset = 0),
            )
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03a_TimelineSuggestionEmptyDayWithLocation() {
    ScreenshotTheme {
        HomeTabRouteFrame(selectedTab = RoutePreviewTab.TIMELINE) {
            TimelinePane(
                uiState = TimelineUiState(items = timelineDays),
                onNewEntry = {},
                onShareMemory = {},
                onOpenDay = {},
                onSearchClick = {},
                onProfileClick = {},
                onHistoryClick = {},
                timelineSuggestion =
                    TimelineSuggestionBlock.EmptyDay(
                        message = "What's going on?",
                        locationName = "Blue Bottle Coffee",
                    ),
            )
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03b_TimelineSuggestionOnThisDayMemoryRecall() {
    ScreenshotTheme {
        HomeTabRouteFrame(selectedTab = RoutePreviewTab.TIMELINE) {
            TimelinePane(
                uiState = TimelineUiState(items = timelineDays),
                onNewEntry = {},
                onShareMemory = {},
                onOpenDay = {},
                onSearchClick = {},
                onProfileClick = {},
                onHistoryClick = {},
                timelineSuggestion =
                    TimelineSuggestionBlock.MemoryRecall(
                        memoryDate = LocalDate(2024, 2, 20),
                        title = "Wrapped up the route inventory and started wiring screenshot helpers.",
                    ),
            )
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S04_TimelineDetailEmptyState() {
    ScreenshotTheme {
        HomeDetailRouteFrame(
            selectedTab = RoutePreviewTab.TIMELINE,
            mainContent = {
                TimelinePane(
                    uiState = TimelineUiState(items = timelineDays),
                    onNewEntry = {},
                    onShareMemory = {},
                    onOpenDay = {},
                    onSearchClick = {},
                    onProfileClick = {},
                    onHistoryClick = {},
                )
            },
            detailContent = { TimelineDetailsEmptyPlaceholder() },
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S05_TimelineDetailPopulated() {
    ScreenshotTheme {
        HomeDetailRouteFrame(
            selectedTab = RoutePreviewTab.TIMELINE,
            mainContent = {
                TimelinePane(
                    uiState = TimelineUiState(items = timelineDays),
                    onNewEntry = {},
                    onShareMemory = {},
                    onOpenDay = {},
                    onSearchClick = {},
                    onProfileClick = {},
                    onHistoryClick = {},
                )
            },
            detailContent = {
                TimelineDayDetailPanel(
                    uiState = timelineDetailState,
                    onExit = {},
                    onOpenLocations = {},
                )
            },
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S06_TimelineDetailNoPeople() {
    ScreenshotTheme {
        HomeDetailRouteFrame(
            selectedTab = RoutePreviewTab.TIMELINE,
            mainContent = {
                TimelinePane(
                    uiState = TimelineUiState(items = timelineDays),
                    onNewEntry = {},
                    onShareMemory = {},
                    onOpenDay = {},
                    onSearchClick = {},
                    onProfileClick = {},
                    onHistoryClick = {},
                )
            },
            detailContent = {
                TimelineDayDetailPanel(
                    uiState = timelineNoPeopleState,
                    onExit = {},
                    onOpenLocations = {},
                )
            },
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S07_JournalsEmpty() {
    ScreenshotTheme {
        HomeTabRouteFrame(selectedTab = RoutePreviewTab.JOURNALS) {
            JournalListPanel(
                journals = emptyList(),
                layoutMode = JournalLayoutMode.CAROUSEL,
                sortOption = JournalSortOption.LAST_UPDATED,
                activeFilters = emptySet(),
                onOpenJournal = {},
                onBrowseJournals = {},
                onCreateJournal = {},
                onToggleLayoutMode = {},
                onSortOptionSelected = {},
                onToggleFilter = {},
            )
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S08_JournalsPopulated() {
    ScreenshotTheme {
        HomeTabRouteFrame(selectedTab = RoutePreviewTab.JOURNALS) {
            JournalListPanel(
                journals = ScreenshotTestData.sampleJournals.map(JournalListItemUiState::ExistingJournal),
                layoutMode = JournalLayoutMode.CAROUSEL,
                sortOption = JournalSortOption.LAST_UPDATED,
                activeFilters = emptySet(),
                onOpenJournal = {},
                onBrowseJournals = {},
                onCreateJournal = {},
                onToggleLayoutMode = {},
                onSortOptionSelected = {},
                onToggleFilter = {},
            )
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S09_RewindNotReady() {
    ScreenshotTheme {
        HomeTabRouteFrame(selectedTab = RoutePreviewTab.REWIND) {
            RewindScreenContent(
                state = RewindOverviewScreenUiState.NotReady(pastRewinds = pastRewinds),
                onOpenRewind = {},
            )
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S10_RewindReady() {
    ScreenshotTheme {
        HomeTabRouteFrame(selectedTab = RoutePreviewTab.REWIND) {
            RewindScreenContent(
                state =
                    RewindOverviewScreenUiState.Ready(
                        pastRewinds = pastRewinds,
                        mostRecentRewind = mostRecentRewind,
                    ),
                onOpenRewind = {},
            )
        }
    }
}
