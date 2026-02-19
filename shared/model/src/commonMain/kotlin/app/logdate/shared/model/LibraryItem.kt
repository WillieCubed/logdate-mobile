package app.logdate.shared.model

import kotlin.time.Instant

data class LibraryItem(
    val id: String,
    val creationTimestamp: Instant,
    val lastUpdated: Instant,
    val uri: String,
)
