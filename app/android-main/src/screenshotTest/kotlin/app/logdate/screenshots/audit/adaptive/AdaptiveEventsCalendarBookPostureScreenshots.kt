package app.logdate.screenshots.audit.adaptive

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.feature.events.ui.calendar.EventsCalendarContent
import app.logdate.feature.events.ui.calendar.EventsCalendarUiState
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.shared.model.Event
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import com.android.tools.screenshot.PreviewTest
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val BOOK_FOLDABLE = "spec:width=1440dp,height=900dp"
private const val TABLETOP_FOLDABLE = "spec:width=1440dp,height=900dp"

private val bookPostureLayoutInfo =
    FoldableLayoutInfo(
        isFoldable = true,
        posture = FoldablePosture.Book,
        hinge =
            FoldableHingeInfo(
                orientation = FoldableHingeOrientation.Vertical,
                state = FoldableHingeState.HalfOpened,
                occlusionType = FoldableOcclusionType.Full,
                bounds =
                    FoldableHingeBounds(
                        left = 708.dp,
                        top = 0.dp,
                        right = 732.dp,
                        bottom = 900.dp,
                        width = 24.dp,
                        height = 900.dp,
                    ),
                isSeparating = true,
            ),
    )

private val tabletopPostureLayoutInfo =
    FoldableLayoutInfo(
        isFoldable = true,
        posture = FoldablePosture.Tabletop,
        hinge =
            FoldableHingeInfo(
                orientation = FoldableHingeOrientation.Horizontal,
                state = FoldableHingeState.HalfOpened,
                occlusionType = FoldableOcclusionType.Full,
                bounds =
                    FoldableHingeBounds(
                        left = 0.dp,
                        top = 438.dp,
                        right = 1440.dp,
                        bottom = 462.dp,
                        width = 1440.dp,
                        height = 24.dp,
                    ),
                isSeparating = true,
            ),
    )

/**
 * A quiet ordinary February with only a handful of events sprinkled across distinct
 * days, plus a selected day whose single appointment populates the day list pane.
 */
private fun eventsCalendarSampleState(): EventsCalendarUiState {
    val dentist =
        Event(
            id = Uuid.parse("00000000-0000-0000-0000-000000000101"),
            title = "Dentist cleaning",
            startTime = Instant.parse("2025-02-06T09:30:00Z"),
            endTime = Instant.parse("2025-02-06T10:15:00Z"),
        )
    val groceries =
        Event(
            id = Uuid.parse("00000000-0000-0000-0000-000000000102"),
            title = "Grocery run",
            startTime = Instant.parse("2025-02-11T17:00:00Z"),
            endTime = Instant.parse("2025-02-11T17:45:00Z"),
        )
    val coffee =
        Event(
            id = Uuid.parse("00000000-0000-0000-0000-000000000103"),
            title = "Coffee with Dana",
            startTime = Instant.parse("2025-02-18T15:00:00Z"),
            endTime = Instant.parse("2025-02-18T16:00:00Z"),
        )
    val laundry =
        Event(
            id = Uuid.parse("00000000-0000-0000-0000-000000000104"),
            title = "Laundry day",
            startTime = Instant.parse("2025-02-23T11:00:00Z"),
            endTime = Instant.parse("2025-02-23T12:00:00Z"),
        )
    val selectedDay = LocalDate(2025, 2, 18)
    return EventsCalendarUiState(
        displayedMonth = LocalDate(2025, 2, 1),
        selectedDay = selectedDay,
        eventsByDay =
            mapOf(
                LocalDate(2025, 2, 6) to listOf(dentist),
                LocalDate(2025, 2, 11) to listOf(groceries),
                selectedDay to listOf(coffee),
                LocalDate(2025, 2, 23) to listOf(laundry),
            ),
        today = LocalDate(2025, 2, 18),
    )
}

@PreviewTest
@Preview(name = "Events calendar book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A123_EventsCalendarBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            EventsCalendarContent(
                uiState = eventsCalendarSampleState(),
                onBack = {},
                onNavigateToEvent = {},
                onPreviousMonth = {},
                onNextMonth = {},
                onSelectDay = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@PreviewTest
@Preview(name = "Events calendar tabletop posture", showBackground = true, device = TABLETOP_FOLDABLE)
@Composable
fun A124_EventsCalendarTabletopPosture() {
    provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
        ScreenshotTheme {
            EventsCalendarContent(
                uiState = eventsCalendarSampleState(),
                onBack = {},
                onNavigateToEvent = {},
                onPreviousMonth = {},
                onNextMonth = {},
                onSelectDay = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
