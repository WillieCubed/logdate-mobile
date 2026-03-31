@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.navigation.routes

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import app.logdate.feature.library.ui.LibraryScreen
import app.logdate.feature.library.ui.components.MediaBoundsTransform
import app.logdate.feature.library.ui.detail.MediaDetailScreen
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.LibraryListRoute
import app.logdate.navigation.routes.core.LibraryMediaDetailRoute
import app.logdate.navigation.scenes.HomeScene
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import app.logdate.ui.common.transitions.TransitionKeys
import kotlin.uuid.Uuid
import app.logdate.navigation.LocalSharedTransitionScope as NavigationSharedTransitionScope
import app.logdate.ui.LocalSharedTransitionScope as FeatureSharedTransitionScope

fun MainAppNavigator.openMediaDetail(mediaId: Uuid) {
    backStack.add(LibraryMediaDetailRoute(mediaId))
}

private typealias SceneTransition = AnimatedContentTransitionScope<Scene<*>>.() -> ContentTransform
private typealias PredictiveSceneTransition = AnimatedContentTransitionScope<Scene<*>>.(Int) -> ContentTransform

private data class RouteTransitions(
    val forward: SceneTransition,
    val pop: SceneTransition,
    val predictivePop: PredictiveSceneTransition,
) {
    fun toMetadata(): RouteMetadata =
        NavDisplay.transitionSpec(forward) +
            NavDisplay.popTransitionSpec(pop) +
            NavDisplay.predictivePopTransitionSpec(predictivePop)
}

private val libraryMediaDetailTransitions =
    RouteTransitions(
        forward = {
            // The shared-bounds morph is the semantic transition here. Keep the library
            // scene in place underneath instead of layering a generic hierarchical slide on top.
            EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished
        },
        pop = {
            EnterTransition.None togetherWith fadeOut()
        },
        predictivePop = { _ ->
            EnterTransition.None togetherWith fadeOut()
        },
    )

private val libraryMediaDetailTransitionMetadata: RouteMetadata =
    libraryMediaDetailTransitions.toMetadata()

/**
 * Provides the navigation routes for library-related screens.
 */
fun EntryProviderScope<NavKey>.libraryRoutes(
    onOpenMediaDetail: (Uuid) -> Unit,
    onBack: () -> Unit,
    onNavigateToJournal: (Uuid) -> Unit,
    onOpenSearch: () -> Unit,
) {
    routeEntry<LibraryListRoute>(
        metadata = HomeScene.homeScene(),
    ) { _ ->
        CompositionLocalProvider(
            FeatureSharedTransitionScope provides NavigationSharedTransitionScope.current,
            LocalNavAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
        ) {
            LibraryScreen(
                onOpenMediaDetail = onOpenMediaDetail,
                onOpenSearch = onOpenSearch,
            )
        }
    }

    routeEntry<LibraryMediaDetailRoute>(
        metadata = libraryMediaDetailTransitionMetadata,
    ) { route ->
        val context = LocalContext.current
        val navigationScope = NavigationSharedTransitionScope.current
        val animatedContentScope = LocalNavAnimatedContentScope.current

        val sharedBoundsModifier =
            if (navigationScope != null) {
                with(navigationScope) {
                    Modifier.sharedBounds(
                        rememberSharedContentState(
                            key = "${TransitionKeys.LIBRARY_MEDIA_TRANSITION}-${route.mediaId}",
                        ),
                        animatedVisibilityScope = animatedContentScope,
                        boundsTransform = MediaBoundsTransform,
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    )
                }
            } else {
                Modifier
            }

        CompositionLocalProvider(
            FeatureSharedTransitionScope provides navigationScope,
            LocalNavAnimatedVisibilityScope provides animatedContentScope,
        ) {
            MediaDetailScreen(
                mediaId = route.mediaId,
                onBack = onBack,
                onNavigateToJournal = onNavigateToJournal,
                onShare = { mediaUri ->
                    val uri = Uri.parse(mediaUri)
                    val mimeType =
                        context.contentResolver.getType(uri) ?: "image/*"
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    val chooser =
                        Intent.createChooser(shareIntent, null).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(chooser)
                },
                modifier = sharedBoundsModifier,
            )
        }
    }
}
