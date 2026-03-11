package app.logdate.screenshots.components.home_timeline

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.location.timeline.ui.LocationTimelineContent
import app.logdate.feature.location.timeline.ui.model.CurrentLocationUiModel
import app.logdate.feature.location.timeline.ui.model.LocationLabelSource
import app.logdate.feature.location.timeline.ui.model.LocationStopUiModel
import app.logdate.feature.location.timeline.ui.model.LocationTimelineErrorUiState
import app.logdate.feature.location.timeline.ui.model.LocationTimelineUiState
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.ui.maps.LocalGoogleMapsAvailabilityOverride
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Instant

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

internal val sampleLocationSuccessState =
    LocationTimelineUiState.Success(
        currentLocation = sampleCurrentLocation,
        stops = sampleStops,
    )

internal val sampleLocationSelectedState =
    LocationTimelineUiState.Success(
        currentLocation = sampleCurrentLocation,
        stops = sampleStops,
        selectedStopId = sampleStops.last().id,
    )

internal val sampleLocationEmptyState =
    LocationTimelineUiState.Success(
        currentLocation = null,
        stops = emptyList(),
    )

// ─── Location Timeline States ───────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LocationTimeline_Loading() {
    ScreenshotTheme {
        LocationTimelineContent(
            uiState = LocationTimelineUiState.Loading,
            onSelectStop = {},
            onDeleteStop = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LocationTimeline_Success() {
    ScreenshotTheme {
        LocationTimelineContent(
            uiState = sampleLocationSuccessState,
            onSelectStop = {},
            onDeleteStop = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LocationTimeline_Success_Dark() {
    ScreenshotTheme(darkTheme = true) {
        LocationTimelineContent(
            uiState = sampleLocationSuccessState,
            onSelectStop = {},
            onDeleteStop = {},
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
            onSelectStop = {},
            onDeleteStop = {},
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
            onSelectStop = {},
            onDeleteStop = {},
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
            onSelectStop = {},
            onDeleteStop = {},
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
            onSelectStop = {},
            onDeleteStop = {},
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
                onSelectStop = {},
                onDeleteStop = {},
            )
        }
    }
}
