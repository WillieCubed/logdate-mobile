package app.logdate.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import app.logdate.feature.core.main.HomeRoute
import app.logdate.ui.navigation.routeClass
import kotlin.reflect.KClass

private typealias SceneTransition = AnimatedContentTransitionScope<Scene<*>>.() -> ContentTransform
private typealias PredictiveSceneTransition = AnimatedContentTransitionScope<Scene<*>>.(Int) -> ContentTransform

private fun sceneRouteClass(scene: Scene<*>?): KClass<out NavKey>? = scene?.entries?.lastOrNull()?.routeClass()

private val editorRouteTransitions =
    RouteTransitions(
        forward = {
            if (sceneRouteClass(initialState) == HomeRoute::class) {
                EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished
            } else {
                slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
            }
        },
        pop = {
            if (sceneRouteClass(targetState) == HomeRoute::class) {
                EnterTransition.None togetherWith fadeOut()
            } else {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            }
        },
        predictivePop = { _ ->
            if (sceneRouteClass(targetState) == HomeRoute::class) {
                EnterTransition.None togetherWith fadeOut()
            } else {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            }
        },
    )

internal val editorRouteTransitionMetadata: Map<String, Any> =
    editorRouteTransitions.toMetadata()

private data class RouteTransitions(
    val forward: SceneTransition,
    val pop: SceneTransition,
    val predictivePop: PredictiveSceneTransition,
) {
    fun toMetadata(): Map<String, Any> =
        NavDisplay.transitionSpec(forward) +
            NavDisplay.popTransitionSpec(pop) +
            NavDisplay.predictivePopTransitionSpec(predictivePop)
}
