package app.logdate.screenshots.components.home_timeline

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.timeline.ui.details.TimelineDayDetailPanel
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.ui.maps.LocalGoogleMapsAvailabilityOverride
import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.timeline.TimelineDayUiState
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDate

private val locationsOnlyState =
    TimelineDayUiState(
        summary = "Mapped the day back to real places and reopened the inline location context.",
        date = LocalDate(2025, 2, 20),
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
                PlaceUiState(
                    id = "place-3",
                    title = "Home",
                    latitude = 37.7694,
                    longitude = -122.4862,
                ),
            ),
    )

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun TimelineLocationSection_Default() {
    ScreenshotTheme {
        TimelineDayDetailPanel(
            uiState = locationsOnlyState,
            onExit = {},
            onOpenLocations = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun TimelineLocationSection_NoMapConfigured() {
    ScreenshotTheme {
        CompositionLocalProvider(LocalGoogleMapsAvailabilityOverride provides false) {
            TimelineDayDetailPanel(
                uiState = locationsOnlyState,
                onExit = {},
                onOpenLocations = {},
            )
        }
    }
}
