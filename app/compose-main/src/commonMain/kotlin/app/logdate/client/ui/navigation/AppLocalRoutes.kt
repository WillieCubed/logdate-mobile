package app.logdate.client.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Top-level routes that don't have a natural feature module home — `Search` is reachable from
 * almost every screen, and `LocationTimeline` is the standalone (non-tab-embedded) entry into
 * the location timeline feature. They live here next to `LogDateNavDisplay` so the rest of
 * the navigation registry can resolve them without a circular feature-module dependency.
 */
@Serializable
data class SearchRoute(
    /** Pre-populates the search input on entry. Empty for an unfiltered cold open. */
    val query: String = "",
    /**
     * Pre-selected type filters as FTS-value strings (e.g. "text_note", "media_caption").
     * Empty list means no type filter. Strings rather than enum to keep the route serializable
     * across the navigation graph.
     */
    val typeFtsValues: List<String> = emptyList(),
    /**
     * Pre-selected date-range filter as the [app.logdate.client.domain.search.DateRangeFilter]
     * enum name (e.g. "Today", "ThisWeek"). Empty string means [DateRangeFilter.AllTime].
     */
    val dateRangeName: String = "",
) : NavKey

@Serializable
data object LocationTimelineRoute : NavKey
