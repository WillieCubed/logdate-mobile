package app.logdate.navigation.scenes

import androidx.navigation3.runtime.NavKey
import app.logdate.feature.core.main.HomeRoute
import app.logdate.feature.events.navigation.EventDetailRoute
import app.logdate.feature.journals.navigation.JournalDetailsRoute
import app.logdate.feature.journals.navigation.JournalsOverviewRoute
import app.logdate.feature.journals.navigation.NoteDetailRoute
import app.logdate.feature.library.navigation.LibraryOverviewRoute
import app.logdate.feature.library.navigation.MediaDetailRoute
import app.logdate.feature.rewind.navigation.RewindDetailRoute
import app.logdate.navigation.TimelineDetailRoute
import kotlin.reflect.KClass

/**
 * Routes that can render alongside their parent in a two-pane layout on wide screens.
 *
 * Excludes routes that should always take the full screen even when wide enough for two-pane,
 * such as `RewindDetailRoute` (immersive playback) and the editor.
 */
private val twoPaneEligibleDetailClasses: Set<KClass<out NavKey>> =
    setOf(
        TimelineDetailRoute::class,
        JournalDetailsRoute::class,
        NoteDetailRoute::class,
        EventDetailRoute::class,
        MediaDetailRoute::class,
    )

/**
 * Routes that can serve as the "list" pane next to a two-pane detail.
 *
 * `HomeRoute` is the main destination for the Timeline / Journals / Library / Rewind tabs;
 * the explicit overview routes are listed for the cases where users navigate directly into
 * an overview without going through the home shell.
 */
private val paneSourceClasses: Set<KClass<out NavKey>> =
    setOf(
        HomeRoute::class,
        JournalsOverviewRoute::class,
        LibraryOverviewRoute::class,
    )

/**
 * Routes that always take the full screen, regardless of available width.
 */
private val alwaysFullscreenClasses: Set<KClass<out NavKey>> =
    setOf(
        RewindDetailRoute::class,
    )

internal fun isTwoPaneEligibleDetail(routeClass: KClass<out NavKey>?): Boolean =
    routeClass != null && routeClass in twoPaneEligibleDetailClasses

internal fun isPaneSource(routeClass: KClass<out NavKey>?): Boolean = routeClass != null && routeClass in paneSourceClasses

internal fun isAlwaysFullscreen(routeClass: KClass<out NavKey>?): Boolean = routeClass != null && routeClass in alwaysFullscreenClasses
