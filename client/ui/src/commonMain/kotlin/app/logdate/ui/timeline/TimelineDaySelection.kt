package app.logdate.ui.timeline

import kotlinx.datetime.LocalDate

sealed class TimelineDaySelection {
    data object NotSelected : TimelineDaySelection()
    
    /**
     * Represents a selected day in the timeline.
     */
    data class DateSelected(
        val date: LocalDate,
    ) : TimelineDaySelection()
    
    /**
     * @deprecated Use DateSelected instead
     */
    data class Selected(
        val id: String,
        val day: LocalDate,
    ) : TimelineDaySelection()
}