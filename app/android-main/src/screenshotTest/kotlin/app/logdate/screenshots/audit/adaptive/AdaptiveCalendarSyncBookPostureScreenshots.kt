package app.logdate.screenshots.audit.adaptive

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.client.calendar.DeviceCalendar
import app.logdate.feature.events.ui.calendarsync.CalendarSyncActivityContent
import app.logdate.feature.events.ui.calendarsync.CalendarSyncCalendarsContent
import app.logdate.feature.events.ui.calendarsync.CalendarSyncCalendarsUiState
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.shared.model.Event
import app.logdate.shared.model.ExternalCalendarSource
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import com.android.tools.screenshot.PreviewTest
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val BOOK_FOLDABLE = "spec:width=1440dp,height=900dp"

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

private fun sampleCalendarsState(): CalendarSyncCalendarsUiState {
    val calendars =
        listOf(
            DeviceCalendar(
                id = "cal-1",
                displayName = "casey.rivera@gmail.com",
                accountName = "Google",
                accountType = "com.google",
                color = 0xFF3F51B5L,
                isPrimary = true,
            ),
            DeviceCalendar(
                id = "cal-2",
                displayName = "Work",
                accountName = "Google",
                accountType = "com.google",
                color = 0xFF009688L,
            ),
            DeviceCalendar(
                id = "cal-3",
                displayName = "Family",
                accountName = "iCloud",
                accountType = "com.apple",
                color = 0xFFE91E63L,
            ),
            DeviceCalendar(
                id = "cal-4",
                displayName = "Birthdays",
                accountName = "iCloud",
                accountType = "com.apple",
                color = 0xFFFF9800L,
            ),
        )
    return CalendarSyncCalendarsUiState(
        isLoading = false,
        calendars = calendars,
        selectedIds = setOf("cal-1", "cal-3"),
    )
}

private fun sampleImportedEvents(): List<Event> {
    val tuesdayMorning = Instant.parse("2026-03-10T09:00:00Z")
    val tuesdayMorningEnd = Instant.parse("2026-03-10T09:30:00Z")
    val tuesdayEvening = Instant.parse("2026-03-10T18:00:00Z")
    val tuesdayEveningEnd = Instant.parse("2026-03-10T18:45:00Z")
    val wednesdayMidday = Instant.parse("2026-03-11T12:00:00Z")
    val wednesdayMiddayEnd = Instant.parse("2026-03-11T12:30:00Z")
    return listOf(
        Event(
            id = Uuid.parse("00000000-0000-0000-0000-000000000101"),
            title = "Team standup",
            startTime = tuesdayMorning,
            endTime = tuesdayMorningEnd,
            externalCalendarId = "Google:evt-101",
            externalCalendarSource = ExternalCalendarSource.DEVICE_CALENDAR,
            created = tuesdayMorning,
            lastUpdated = tuesdayMorning,
        ),
        Event(
            id = Uuid.parse("00000000-0000-0000-0000-000000000102"),
            title = "Dentist appointment",
            startTime = tuesdayEvening,
            endTime = tuesdayEveningEnd,
            externalCalendarId = "Google:evt-102",
            externalCalendarSource = ExternalCalendarSource.DEVICE_CALENDAR,
            created = tuesdayEvening,
            lastUpdated = tuesdayEvening,
        ),
        Event(
            id = Uuid.parse("00000000-0000-0000-0000-000000000103"),
            title = "Lunch with Sam",
            startTime = wednesdayMidday,
            endTime = wednesdayMiddayEnd,
            externalCalendarId = "iCloud:evt-103",
            externalCalendarSource = ExternalCalendarSource.DEVICE_CALENDAR,
            created = wednesdayMidday,
            lastUpdated = wednesdayMidday,
        ),
    )
}

@PreviewTest
@Preview(name = "Calendar sync calendars book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A125_CalendarSyncCalendarsBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            CalendarSyncCalendarsContent(
                state = sampleCalendarsState(),
                onBack = {},
                onToggle = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@PreviewTest
@Preview(name = "Calendar sync activity book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A126_CalendarSyncActivityBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            CalendarSyncActivityContent(
                events = sampleImportedEvents(),
                onBack = {},
                onNavigateToEvent = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
