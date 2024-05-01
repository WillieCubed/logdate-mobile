package app.logdate.model

import kotlinx.datetime.Instant

data class LibraryItem(
    val id: String,
    val creationTimestamp: Instant,
    val lastUpdated: Instant,
)
