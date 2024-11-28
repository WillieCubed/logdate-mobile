package app.logdate.shared.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class Journal(
    val id: String = "", // Blank for convenience constructor
    val title: String = "",
    val description: String = "",
    val isFavorited: Boolean = false, // TODO: Move to separate user journal data repository
    val created: Instant = Clock.System.now(),
    val lastUpdated: Instant = Clock.System.now(),
)
