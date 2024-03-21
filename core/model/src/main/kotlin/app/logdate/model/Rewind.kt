package app.logdate.model

import kotlinx.datetime.Instant

/**
 * A single item in the timeline.
 */
data class Rewind(
    val uid: String,
    val date: Instant,
    val label: String,
    val title: String,
)