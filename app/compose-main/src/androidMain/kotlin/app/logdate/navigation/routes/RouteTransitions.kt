package app.logdate.navigation.routes

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay

internal typealias SceneTransition = AnimatedContentTransitionScope<Scene<*>>.() -> ContentTransform
internal typealias PredictiveSceneTransition = AnimatedContentTransitionScope<Scene<*>>.(Int) -> ContentTransform

internal data class RouteTransitions(
    val forward: SceneTransition,
    val pop: SceneTransition,
    val predictivePop: PredictiveSceneTransition,
) {
    fun toMetadata(): RouteMetadata =
        NavDisplay.transitionSpec(forward) +
            NavDisplay.popTransitionSpec(pop) +
            NavDisplay.predictivePopTransitionSpec(predictivePop)
}
