package app.logdate.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Per-day timeline detail route. The `LogDateNavDisplay` entry decodes the date and renders
 * `TimelineDayDetailPanel` from `client/feature/timeline`.
 *
 * `LocalDate` is serialized as an ISO-8601 string so iOS / web save-state can round-trip it
 * without a custom serializer.
 */
@Serializable
data class TimelineDetailRoute(
    val dateIso: String,
) : NavKey
