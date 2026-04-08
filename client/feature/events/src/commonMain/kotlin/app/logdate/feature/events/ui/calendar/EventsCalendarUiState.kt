package app.logdate.feature.events.ui.calendar

import app.logdate.shared.model.Event
import kotlinx.datetime.LocalDate

/**
 * UI state for the events calendar surface.
 *
 * The screen renders a month grid with one cell per day. Each cell shows the count of
 * events on that day so the user can scan an overview at a glance, then drill into the
 * selected day to see the actual list. [eventsByDay] is the source of truth for both
 * the cell badges and the selected-day list.
 */
data class EventsCalendarUiState(
    /** First day of the month being displayed. */
    val displayedMonth: LocalDate,
    /** Day the user has tapped, or `null` for "no selection". */
    val selectedDay: LocalDate?,
    /** Events grouped by their starting date for [displayedMonth]. */
    val eventsByDay: Map<LocalDate, List<Event>> = emptyMap(),
    /** Today's date in the device's local timezone — used to highlight the "today" cell. */
    val today: LocalDate,
)
