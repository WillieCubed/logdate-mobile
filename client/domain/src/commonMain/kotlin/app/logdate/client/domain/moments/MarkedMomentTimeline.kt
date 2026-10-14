package app.logdate.client.domain.moments

import app.logdate.client.domain.timeline.Timeline
import app.logdate.client.domain.timeline.TimelineDay
import app.logdate.client.repository.moments.MarkedMoment
import kotlinx.datetime.LocalDate

internal fun withTimelineMarks(
    timeline: Timeline,
    marksByDay: Map<LocalDate, List<MarkedMoment>>,
): Timeline {
    val days = timeline.days.associateBy { it.date }.toMutableMap()
    marksByDay.forEach { (date, marks) ->
        if (marks.isNotEmpty()) {
            val existing = days[date]
            days[date] = existing?.copy(marks = marks) ?: TimelineDay(
                start = marks.minOf { it.capturedAt },
                end = marks.maxOf { it.capturedAt },
                tldr = "",
                date = date,
                marks = marks,
            )
        }
    }
    return Timeline(days.values.sortedByDescending { it.date })
}
