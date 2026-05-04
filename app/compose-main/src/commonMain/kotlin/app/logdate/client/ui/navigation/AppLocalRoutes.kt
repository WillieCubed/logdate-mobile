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
data object SearchRoute : NavKey

@Serializable
data object LocationTimelineRoute : NavKey
