package app.logdate.model

import kotlinx.datetime.Instant

/**
 * A single item in the timeline.
 */
data class TimelineItem(
    val uid: String,
    val date: Instant,
)