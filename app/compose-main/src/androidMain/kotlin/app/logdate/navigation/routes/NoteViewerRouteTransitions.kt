package app.logdate.navigation.routes

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavKey
import app.logdate.navigation.routes.core.JournalDetail
import app.logdate.navigation.routes.core.LocationRoute
import kotlin.reflect.KClass

internal fun supportsNoteViewerSharedTransition(routeClass: KClass<out NavKey>?): Boolean =
    routeClass == JournalDetail::class || routeClass == LocationRoute::class

internal val noteViewerRouteTransitions =
    RouteTransitions(
        forward = {
            val fromRoute = sceneRouteClass(initialState)

            if (supportsNoteViewerSharedTransition(fromRoute)) {
                // When note viewer opens from a route that already exposes a note card,
                // the shared-bounds morph should own the transition instead of layering
                // a generic scene slide underneath it.
                EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished
            } else {
                slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
            }
        },
        pop = {
            val toRoute = sceneRouteClass(targetState)

            if (supportsNoteViewerSharedTransition(toRoute)) {
                EnterTransition.None togetherWith fadeOut()
            } else {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            }
        },
        predictivePop = { _ ->
            val toRoute = sceneRouteClass(targetState)

            if (supportsNoteViewerSharedTransition(toRoute)) {
                EnterTransition.None togetherWith fadeOut()
            } else {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            }
        },
    )

internal val noteViewerRouteTransitionMetadata: RouteMetadata =
    noteViewerRouteTransitions.toMetadata()
