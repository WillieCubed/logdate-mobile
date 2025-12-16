package app.logdate.server.types

import kotlinx.serialization.Serializable

@Serializable
data class DateRange(
    val start: String,
    val end: String
)