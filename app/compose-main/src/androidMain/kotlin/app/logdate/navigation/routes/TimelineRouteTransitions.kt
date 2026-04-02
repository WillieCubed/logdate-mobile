package app.logdate.navigation.routes

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.scene.Scene
import app.logdate.navigation.routes.core.TimelineListRoute
import app.logdate.navigation.scenes.ListDetailHomeScene

private fun isExpandedTimelineListDetailScene(scene: Scene<*>?): Boolean = scene is ListDetailHomeScene<*>

internal val timelineDetailRouteTransitions =
    RouteTransitions(
        forward = {
            val fromRoute = sceneRouteClass(initialState)

            if (
                fromRoute == TimelineListRoute::class &&
                isExpandedTimelineListDetailScene(targetState)
            ) {
                // On large list-detail layouts, selecting a day should keep the list scene
                // stable and let the detail pane activation own the transition.
                EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished
            } else {
                slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
            }
        },
        pop = {
            val toRoute = sceneRouteClass(targetState)

            if (
                toRoute == TimelineListRoute::class &&
                isExpandedTimelineListDetailScene(initialState)
            ) {
                EnterTransition.None togetherWith fadeOut()
            } else {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            }
        },
        predictivePop = { _ ->
            val toRoute = sceneRouteClass(targetState)

            if (
                toRoute == TimelineListRoute::class &&
                isExpandedTimelineListDetailScene(initialState)
            ) {
                EnterTransition.None togetherWith fadeOut()
            } else {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            }
        },
    )

internal val timelineDetailRouteTransitionMetadata: RouteMetadata =
    timelineDetailRouteTransitions.toMetadata()
