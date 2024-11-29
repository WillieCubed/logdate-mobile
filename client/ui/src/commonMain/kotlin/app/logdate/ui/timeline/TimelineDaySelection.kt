package app.logdate.ui.timeline

import kotlinx.datetime.LocalDate

sealed class TimelineDaySelection {
    data object NotSelected : TimelineDaySelection()
    data class Selected(
        val id: String,
        val day: LocalDate,
    ) : TimelineDaySelection()
}