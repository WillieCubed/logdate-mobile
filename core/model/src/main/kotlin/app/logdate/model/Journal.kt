package app.logdate.model

import kotlinx.datetime.Instant

data class Journal(
    val id: String = "", // Blank for convenience constructor
    val title: String,
    val description: String,
    val isFavorited: Boolean, // TODO: Move to separate user journal data repository
    val created: Instant,
    val lastUpdated: Instant,
)
