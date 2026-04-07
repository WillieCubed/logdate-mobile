package app.logdate.screenshots.components.home_timeline

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.client.domain.location.LocationMemoryTimeFilter
import app.logdate.feature.location.timeline.ui.LocationTimelineContent
import app.logdate.feature.location.timeline.ui.model.CurrentLocationUiModel
import app.logdate.feature.location.timeline.ui.model.LocationLabelSource
import app.logdate.feature.location.timeline.ui.model.LocationMemoryKind
import app.logdate.feature.location.timeline.ui.model.LocationMemoryPreviewUiModel
import app.logdate.feature.location.timeline.ui.model.LocationPlaceUiModel
import app.logdate.feature.location.timeline.ui.model.LocationStopUiModel
import app.logdate.feature.location.timeline.ui.model.LocationTimelineErrorUiState
import app.logdate.feature.location.timeline.ui.model.LocationTimelineUiState
import app.logdate.screenshots.common.LargeScreenAuditPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.ui.maps.LocalGoogleMapsAvailabilityOverride
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal val sampleCurrentLocation =
    CurrentLocationUiModel(
        title = "Blue Bottle Coffee",
        subtitle = "123 Main St, San Francisco, CA",
        latitude = 37.7749,
        longitude = -122.4194,
    )

internal val sampleStops = listOf(
    LocationStopUiModel(
        id = "1",
        title = "Golden Gate Park",
        subtitle = "501 Stanyan St, San Francisco, CA",
        latitude = 37.7749,
        longitude = -122.4194,
        startedAt = "2:15 PM",
        endedAt = "3:00 PM",
        timeRange = "2:15 PM - 3:00 PM",
        duration = "Stayed for 45m",
        sourceLabel = "Google Places",
        source = LocationLabelSource.GOOGLE_PLACES,
        sampleCount = 8,
        startTime = Instant.fromEpochMilliseconds(1_740_000_000_000L),
        endTime = Instant.fromEpochMilliseconds(1_740_002_700_000L),
        hasReliableDuration = true,
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
        duration = "Stayed for 1h 30m",
        sourceLabel = "Saved place",
        source = LocationLabelSource.USER_DEFINED,
        sampleCount = 12,
        startTime = Instant.fromEpochMilliseconds(1_739_990_000_000L),
        endTime = Instant.fromEpochMilliseconds(1_739_995_400_000L),
        hasReliableDuration = true,
    ),
)

internal val samplePlaces = listOf(
    LocationPlaceUiModel(
        id = "place-1",
        title = "Golden Gate Park",
        subtitle = "2 memories · San Francisco, CA",
        latitude = 37.7749,
        longitude = -122.4194,
        lastVisitedLabel = "Visited today",
        memoryCount = 2,
        sourceLabel = "Google Places",
        source = LocationLabelSource.GOOGLE_PLACES,
        memories =
            listOf(
                LocationMemoryPreviewUiModel(
                    noteId = Uuid.parse("00000000-0000-0000-0000-000000000111"),
                    title = "Afternoon walk",
                    subtitle = "Text memory",
                    timestamp = Instant.fromEpochMilliseconds(1_740_002_700_000L),
                    latitude = 37.7749,
                    longitude = -122.4194,
                    kind = LocationMemoryKind.TEXT,
                ),
                LocationMemoryPreviewUiModel(
                    noteId = Uuid.parse("00000000-0000-0000-0000-000000000112"),
                    title = "Park snapshot",
                    subtitle = "Photo memory",
                    timestamp = Instant.fromEpochMilliseconds(1_740_001_800_000L),
                    latitude = 37.7749,
                    longitude = -122.4194,
                    kind = LocationMemoryKind.PHOTO,
                ),
            ),
        relatedStops = listOf(sampleStops.first()),
    ),
    LocationPlaceUiModel(
        id = "place-2",
        title = "Home",
        subtitle = "1 memory · San Francisco, CA",
        latitude = 37.7694,
        longitude = -122.4862,
        lastVisitedLabel = "Visited yesterday",
        memoryCount = 1,
        sourceLabel = "Saved place",
        source = LocationLabelSource.USER_DEFINED,
        memories =
            listOf(
                LocationMemoryPreviewUiModel(
                    noteId = Uuid.parse("00000000-0000-0000-0000-000000000113"),
                    title = "Quiet evening",
                    subtitle = "Audio memory",
                    timestamp = Instant.fromEpochMilliseconds(1_739_995_400_000L),
                    latitude = 37.7694,
                    longitude = -122.4862,
                    kind = LocationMemoryKind.AUDIO,
                ),
            ),
        relatedStops = listOf(sampleStops.last()),
    ),
)

internal val sampleLocationContentState =
    LocationTimelineUiState.Content(
        isLoadingCurrentLocation = false,
        isLoadingPlaces = false,
        isLoadingStops = false,
        currentLocation = sampleCurrentLocation,
        selectedFilter = LocationMemoryTimeFilter.Last30Days,
        places = samplePlaces,
        visiblePlaces = samplePlaces,
        recentStops = sampleStops,
        selectedPlaceId = samplePlaces.first().id,
        canLoadMorePlaces = true,
    )

internal val sampleLocationSelectedState =
    LocationTimelineUiState.Content(
        isLoadingCurrentLocation = false,
        isLoadingPlaces = false,
        isLoadingStops = false,
        currentLocation = sampleCurrentLocation,
        selectedFilter = LocationMemoryTimeFilter.Last30Days,
        places = samplePlaces,
        visiblePlaces = samplePlaces,
        recentStops = sampleStops,
        selectedPlaceId = samplePlaces.last().id,
    )

internal val sampleLocationEmptyState =
    LocationTimelineUiState.Content(
        isLoadingCurrentLocation = false,
        isLoadingPlaces = false,
        isLoadingStops = false,
        currentLocation = null,
        selectedFilter = LocationMemoryTimeFilter.Last30Days,
        places = emptyList(),
        visiblePlaces = emptyList(),
        recentStops = emptyList(),
    )

// ─── Location Timeline States ───────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LocationTimeline_Loading() {
    ScreenshotTheme {
        LocationTimelineContent(
            uiState = LocationTimelineUiState.Content(),
            onSelectPlace = {},
            onDismissPlaceDetail = {},
            onDeleteStop = {},
            onSelectFilter = {},
            onLoadMorePlaces = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LocationTimeline_PartiallyLoaded() {
    ScreenshotTheme {
        LocationTimelineContent(
            uiState = LocationTimelineUiState.Content(
                isLoadingCurrentLocation = false,
                isLoadingPlaces = false,
                isLoadingStops = true,
                currentLocation = sampleCurrentLocation,
                places = samplePlaces,
                visiblePlaces = samplePlaces,
                selectedPlaceId = samplePlaces.first().id,
            ),
            onSelectPlace = {},
            onDismissPlaceDetail = {},
            onDeleteStop = {},
            onSelectFilter = {},
            onLoadMorePlaces = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LocationTimeline_Content() {
    ScreenshotTheme {
        LocationTimelineContent(
            uiState = sampleLocationContentState,
            onSelectPlace = {},
            onDismissPlaceDetail = {},
            onDeleteStop = {},
            onSelectFilter = {},
            onLoadMorePlaces = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LocationTimeline_Content_Dark() {
    ScreenshotTheme(darkTheme = true) {
        LocationTimelineContent(
            uiState = sampleLocationContentState,
            onSelectPlace = {},
            onDismissPlaceDetail = {},
            onDeleteStop = {},
            onSelectFilter = {},
            onLoadMorePlaces = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LocationTimeline_Empty() {
    ScreenshotTheme {
        LocationTimelineContent(
            uiState = sampleLocationEmptyState,
            onSelectPlace = {},
            onDismissPlaceDetail = {},
            onDeleteStop = {},
            onSelectFilter = {},
            onLoadMorePlaces = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LocationTimeline_Error() {
    ScreenshotTheme {
        LocationTimelineContent(
            uiState = LocationTimelineUiState.Error(LocationTimelineErrorUiState.TemporarilyUnavailable),
            onSelectPlace = {},
            onDismissPlaceDetail = {},
            onDeleteStop = {},
            onSelectFilter = {},
            onLoadMorePlaces = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LocationTimeline_PermissionRequired() {
    ScreenshotTheme {
        LocationTimelineContent(
            uiState = LocationTimelineUiState.Error(LocationTimelineErrorUiState.PermissionRequired),
            onSelectPlace = {},
            onDismissPlaceDetail = {},
            onDeleteStop = {},
            onSelectFilter = {},
            onLoadMorePlaces = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LocationTimeline_ServicesDisabled() {
    ScreenshotTheme {
        LocationTimelineContent(
            uiState = LocationTimelineUiState.Error(LocationTimelineErrorUiState.LocationServicesDisabled),
            onSelectPlace = {},
            onDismissPlaceDetail = {},
            onDeleteStop = {},
            onSelectFilter = {},
            onLoadMorePlaces = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LocationTimeline_NoMapConfigured() {
    ScreenshotTheme {
        CompositionLocalProvider(LocalGoogleMapsAvailabilityOverride provides false) {
            LocationTimelineContent(
                uiState = sampleLocationSelectedState,
                onSelectPlace = {},
                onDismissPlaceDetail = {},
                onDeleteStop = {},
                onSelectFilter = {},
                onLoadMorePlaces = {},
            )
        }
    }
}

@PreviewTest
@LargeScreenAuditPreviewMatrix
@Composable
fun LocationTimeline_Content_LargeScreen() {
    ScreenshotTheme {
        CompositionLocalProvider(LocalGoogleMapsAvailabilityOverride provides false) {
            LocationTimelineContent(
                uiState = sampleLocationContentState,
                onSelectPlace = {},
                onDismissPlaceDetail = {},
                onDeleteStop = {},
                onSelectFilter = {},
                onLoadMorePlaces = {},
            )
        }
    }
}
