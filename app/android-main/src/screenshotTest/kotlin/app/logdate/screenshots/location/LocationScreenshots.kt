package app.logdate.screenshots.location

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.location.timeline.ui.LocationTimelineContent
import app.logdate.feature.location.timeline.ui.model.LocationTimelineItem
import app.logdate.feature.location.timeline.ui.model.LocationTimelineUiState
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

private val sampleLocations = listOf(
    LocationTimelineItem(
        id = "1",
        placeName = "Blue Bottle Coffee",
        address = "123 Main St, San Francisco, CA",
        latitude = 37.7749,
        longitude = -122.4194,
        timestamp = 1_740_000_000_000L,
        timeAgo = "2 hours ago",
        duration = "45 min",
        isCurrentLocation = true,
    ),
    LocationTimelineItem(
        id = "2",
        placeName = "Golden Gate Park",
        address = "501 Stanyan St, San Francisco, CA",
        latitude = 37.7694,
        longitude = -122.4862,
        timestamp = 1_739_990_000_000L,
        timeAgo = "5 hours ago",
        duration = "1 hr 30 min",
        isCurrentLocation = false,
    ),
    LocationTimelineItem(
        id = "3",
        placeName = "Home",
        address = "456 Oak St, San Francisco, CA",
        latitude = 37.7700,
        longitude = -122.4300,
        timestamp = 1_739_980_000_000L,
        timeAgo = "8 hours ago",
        isCurrentLocation = false,
    ),
)

// ─── Location Timeline States ───────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LocationTimeline_Loading() {
    ScreenshotTheme {
        LocationTimelineContent(
            uiState = LocationTimelineUiState.Loading,
            onDeleteLocation = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LocationTimeline_Success() {
    ScreenshotTheme {
        LocationTimelineContent(
            uiState = LocationTimelineUiState.Success(
                currentLocation = sampleLocations.first(),
                locationHistory = sampleLocations.drop(1),
            ),
            onDeleteLocation = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LocationTimeline_Success_Dark() {
    ScreenshotTheme(darkTheme = true) {
        LocationTimelineContent(
            uiState = LocationTimelineUiState.Success(
                currentLocation = sampleLocations.first(),
                locationHistory = sampleLocations.drop(1),
            ),
            onDeleteLocation = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LocationTimeline_Empty() {
    ScreenshotTheme {
        LocationTimelineContent(
            uiState = LocationTimelineUiState.Success(
                currentLocation = null,
                locationHistory = emptyList(),
            ),
            onDeleteLocation = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun LocationTimeline_Error() {
    ScreenshotTheme {
        LocationTimelineContent(
            uiState = LocationTimelineUiState.Error("Unable to load location history. Please check your connection."),
            onDeleteLocation = {},
        )
    }
}
