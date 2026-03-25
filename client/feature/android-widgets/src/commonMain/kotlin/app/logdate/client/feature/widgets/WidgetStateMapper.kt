package app.logdate.client.feature.widgets

import app.logdate.client.domain.recommendation.MemoryRecallData
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

internal fun MemoryRecallData.toWidgetState(fallbackSummary: String = ""): OnThisDayWidgetState.HasMemory =
    OnThisDayWidgetState.HasMemory(
        dateIso = date.toString(),
        dateFormatted = date.formatForDisplay(),
        summary = summary.ifEmpty { fallbackSummary },
        thumbnailUri = mediaUris.firstOrNull(),
    )

internal fun LocalDate.formatForDisplay(): String {
    val monthName =
        when (month) {
            Month.JANUARY -> "January"
            Month.FEBRUARY -> "February"
            Month.MARCH -> "March"
            Month.APRIL -> "April"
            Month.MAY -> "May"
            Month.JUNE -> "June"
            Month.JULY -> "July"
            Month.AUGUST -> "August"
            Month.SEPTEMBER -> "September"
            Month.OCTOBER -> "October"
            Month.NOVEMBER -> "November"
            Month.DECEMBER -> "December"
        }
    return "$monthName $day, $year"
}
