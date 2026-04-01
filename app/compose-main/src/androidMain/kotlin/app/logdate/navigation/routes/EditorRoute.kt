@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.logdate.navigation.routes

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import app.logdate.feature.editor.ui.LocalSharedTransitionScope
import app.logdate.feature.editor.ui.NoteEditorScreen
import app.logdate.navigation.routes.core.EntryEditor
import app.logdate.navigation.scenes.HomeTab
import app.logdate.ui.common.transitions.TransitionKeys
import kotlin.reflect.KClass
import app.logdate.feature.editor.ui.LocalAnimatedVisibilityScope as EditorLocalAnimatedVisibilityScope
import app.logdate.navigation.LocalSharedTransitionScope as NavigationLocalSharedTransitionScope

// Front-loads the bounds movement so the shape rounding is visible early in the transition
// rather than only at the very end when the bounds approach FAB size.
private val FabEditorBoundsTransform =
    BoundsTransform { _, _ ->
        tween(durationMillis = 350, easing = FastOutSlowInEasing)
    }

private fun isMainTabRoute(routeClass: KClass<out NavKey>?): Boolean = HomeTab.entries.any { it.route::class == routeClass }

private val editorRouteTransitions =
    RouteTransitions(
        forward = {
            val fromRoute = sceneRouteClass(initialState)

            if (isMainTabRoute(fromRoute)) {
                // When the editor opens from a home tab, the FAB shared-bounds morph is
                // the semantic transition. Suppress the generic scene slide in that case.
                EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished
            } else {
                slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
            }
        },
        pop = {
            val toRoute = sceneRouteClass(targetState)

            if (isMainTabRoute(toRoute)) {
                EnterTransition.None togetherWith fadeOut()
            } else {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            }
        },
        predictivePop = { _ ->
            val toRoute = sceneRouteClass(targetState)

            if (isMainTabRoute(toRoute)) {
                EnterTransition.None togetherWith fadeOut()
            } else {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            }
        },
    )

private val editorRouteTransitionMetadata: RouteMetadata =
    editorRouteTransitions.toMetadata()

/**
 * Provides the navigation routes for the note editor screen.
 *
 * This function sets up the entry for the note editor with custom transition animations.
 *
 * @param onBack Callback to handle back navigation.
 * @param onSave Callback triggered after the entry is saved
 */
fun EntryProviderScope<NavKey>.editorRoutes(
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    routeEntry<EntryEditor>(
        metadata = editorRouteTransitionMetadata,
    ) { route ->
        val navigationScope = NavigationLocalSharedTransitionScope.current
        val animatedContentScope = LocalNavAnimatedContentScope.current

        val sharedBoundsModifier =
            if (navigationScope != null) {
                with(navigationScope) {
                    Modifier.sharedBounds(
                        rememberSharedContentState(key = TransitionKeys.FAB_TO_EDITOR_TRANSITION),
                        animatedVisibilityScope = animatedContentScope,
                        boundsTransform = FabEditorBoundsTransform,
                        clipInOverlayDuringTransition = OverlayClip(MaterialTheme.shapes.large),
                    )
                }
            } else {
                Modifier
            }

        CompositionLocalProvider(
            LocalSharedTransitionScope provides navigationScope,
            EditorLocalAnimatedVisibilityScope provides animatedContentScope,
        ) {
            NoteEditorScreen(
                onNavigateBack = onBack,
                onEntrySaved = onSave,
                entryId = route.id,
                draftId = route.draftId,
                journalIds = route.journalIds,
                modifier = sharedBoundsModifier,
            )
        }
    }
}
