package app.logdate.screenshots.flows.flow02_home_timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.location.timeline.ui.LocationTimelineContent
import app.logdate.feature.location.timeline.ui.LocationTimelineQuickPeekSheet
import app.logdate.feature.location.timeline.ui.model.CurrentLocationUiModel
import app.logdate.feature.location.timeline.ui.model.LocationLabelSource
import app.logdate.feature.location.timeline.ui.model.LocationStopUiModel
import app.logdate.feature.location.timeline.ui.model.LocationTimelineErrorUiState
import app.logdate.feature.location.timeline.ui.model.LocationTimelineUiState
import app.logdate.screenshots.common.HomeTabRouteFrame
import app.logdate.screenshots.common.RoutePreviewTab
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.ui.maps.LocalGoogleMapsAvailabilityOverride
import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelinePane
import app.logdate.ui.timeline.TimelineUiState
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

private val sampleCurrentLocation =
    CurrentLocationUiModel(
        title = "Blue Bottle Coffee",
        subtitle = "123 Main St, San Francisco, CA",
        latitude = 37.7749,
        longitude = -122.4194,
    )

private val sampleStops =
    listOf(
        LocationStopUiModel(
            id = "1",
            title = "Golden Gate Park",
            subtitle = "501 Stanyan St, San Francisco, CA",
            latitude = 37.7749,
            longitude = -122.4194,
            startedAt = "2:15 PM",
            endedAt = "3:00 PM",
            timeRange = "2:15 PM - 3:00 PM",
            duration = "45m",
            sourceLabel = "Google Places",
            source = LocationLabelSource.GOOGLE_PLACES,
            sampleCount = 8,
            startTime = Instant.fromEpochMilliseconds(1_740_000_000_000L),
            endTime = Instant.fromEpochMilliseconds(1_740_002_700_000L),
        ),
        LocationStopUiModel(
            id = "2",
            title = "Home",
            subtitle = "456 Oak St, San Francisco, CA",
            latitude = 37.7694,
            longitude = -122.4862,
            startedAt = "10:30 AM",
            endedAt = "12:00 PM",
            timeRange = "10:30 AM - 12:00 PM",
            duration = "1h 30m",
            sourceLabel = "Saved place",
            source = LocationLabelSource.USER_DEFINED,
            sampleCount = 12,
            startTime = Instant.fromEpochMilliseconds(1_739_990_000_000L),
            endTime = Instant.fromEpochMilliseconds(1_739_995_400_000L),
        ),
    )

private val sampleLocationSelectedState =
    LocationTimelineUiState.Success(
        currentLocation = sampleCurrentLocation,
        stops = sampleStops,
        selectedStopId = sampleStops.last().id,
    )

private val sampleLocationEmptyState =
    LocationTimelineUiState.Success(
        currentLocation = null,
        stops = emptyList(),
    )

private val sampleLocationErrorState =
    LocationTimelineUiState.Error(
        LocationTimelineErrorUiState.TemporarilyUnavailable,
    )

private val timelineQuickPeekDays =
    listOf(
        TimelineDayUiState(
            summary = "Stopped by Blue Bottle before heading to Dolores Park and logging a few notes.",
            date = LocalDate(2025, 2, 20),
            placesVisited =
                listOf(
                    PlaceUiState(id = "place-1", title = "Blue Bottle Coffee"),
                    PlaceUiState(id = "place-2", title = "Dolores Park"),
                ),
        ),
        TimelineDayUiState(
            summary = "Wrapped up errands and ended the evening back at home.",
            date = LocalDate(2025, 2, 19),
            placesVisited = listOf(PlaceUiState(id = "place-3", title = "Home")),
        ),
    )

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S01_LocationPopulated() {
    ScreenshotTheme {
        HomeTabRouteFrame(selectedTab = RoutePreviewTab.LOCATION) {
            LocationTimelineContent(
                uiState = sampleLocationSelectedState,
                onSelectStop = {},
                onDeleteStop = {},
            )
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S02_LocationEmpty() {
    ScreenshotTheme {
        HomeTabRouteFrame(selectedTab = RoutePreviewTab.LOCATION) {
            LocationTimelineContent(
                uiState = sampleLocationEmptyState,
                onSelectStop = {},
                onDeleteStop = {},
            )
        }
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_LocationUnavailable() {
    ScreenshotTheme {
        HomeTabRouteFrame(selectedTab = RoutePreviewTab.LOCATION) {
            LocationTimelineContent(
                uiState = sampleLocationErrorState,
                onSelectStop = {},
                onDeleteStop = {},
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S04_TimelineLocationQuickPeek() {
    ScreenshotTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            HomeTabRouteFrame(selectedTab = RoutePreviewTab.TIMELINE) {
                TimelinePane(
                    uiState = TimelineUiState(items = timelineQuickPeekDays),
                    onNewEntry = {},
                    onShareMemory = {},
                    onOpenDay = {},
                    onSearchClick = {},
                    onProfileClick = {},
                    onHistoryClick = {},
                )
            }

            LocationTimelineQuickPeekSheet(
                uiState = sampleLocationSelectedState,
                onDismissRequest = {},
                onOpenFullTimeline = {},
                onSelectStop = {},
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S05_LocationQuickPeekEmpty() {
    ScreenshotTheme {
        LocationTimelineQuickPeekSheet(
            uiState = sampleLocationEmptyState,
            onDismissRequest = {},
            onOpenFullTimeline = {},
            onSelectStop = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S06_LocationQuickPeekLoading() {
    ScreenshotTheme {
        LocationTimelineQuickPeekSheet(
            uiState = LocationTimelineUiState.Loading,
            onDismissRequest = {},
            onOpenFullTimeline = {},
            onSelectStop = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S07_LocationQuickPeekPermissionRequired() {
    ScreenshotTheme {
        LocationTimelineQuickPeekSheet(
            uiState = LocationTimelineUiState.Error(LocationTimelineErrorUiState.PermissionRequired),
            onDismissRequest = {},
            onOpenFullTimeline = {},
            onSelectStop = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S08_LocationQuickPeekNoMapConfigured() {
    ScreenshotTheme {
        CompositionLocalProvider(LocalGoogleMapsAvailabilityOverride provides false) {
            LocationTimelineQuickPeekSheet(
                uiState = sampleLocationSelectedState,
                onDismissRequest = {},
                onOpenFullTimeline = {},
                onSelectStop = {},
            )
        }
    }
}
