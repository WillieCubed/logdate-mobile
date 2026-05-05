package app.logdate.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Per-day timeline detail route. The `LogDateNavDisplay` entry decodes the date and renders
 * `TimelineDayDetailPanel` from `client/feature/timeline`.
 *
 * `LocalDate` is serialized as an ISO-8601 string so iOS / web save-state can round-trip it
 * without a custom serializer.
 *
 * @property entryId Optional UUID of an entry within the day to highlight on arrival. Set by
 *   the search-result tap fallback for content types without a dedicated detail screen
 *   (transcription, ambient sound, sticker, place). The timeline panel does not yet consume
 *   this value — wiring scroll-to-entry needs per-entry `key` slots in
 *   `TimelineDayDetailPanel`'s LazyColumn (currently keyed by `contentType` section names).
 *   The field is plumbed end-to-end so that hook is one composable change away.
 */
@Serializable
data class TimelineDetailRoute(
    val dateIso: String,
    val entryId: String? = null,
) : NavKey
