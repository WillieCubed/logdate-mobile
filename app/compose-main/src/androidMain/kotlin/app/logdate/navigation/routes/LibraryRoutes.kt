@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.navigation.routes

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import app.logdate.feature.library.ui.LibraryScreen
import app.logdate.feature.library.ui.components.LIBRARY_MEDIA_TRANSITION_KEY
import app.logdate.feature.library.ui.detail.MediaDetailScreen
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.LibraryListRoute
import app.logdate.navigation.routes.core.LibraryMediaDetailRoute
import app.logdate.navigation.scenes.HomeScene
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import kotlin.uuid.Uuid
import app.logdate.navigation.LocalSharedTransitionScope as NavigationSharedTransitionScope
import app.logdate.ui.LocalSharedTransitionScope as FeatureSharedTransitionScope

/**
 * Bounds transform matching the thumbnail's transition curve.
 */
private val MediaBoundsTransform =
    BoundsTransform { _, _ ->
        tween(durationMillis = 400, easing = FastOutSlowInEasing)
    }

fun MainAppNavigator.openMediaDetail(mediaId: Uuid) {
    backStack.add(LibraryMediaDetailRoute(mediaId))
}

/**
 * Provides the navigation routes for library-related screens.
 */
fun EntryProviderScope<NavKey>.libraryRoutes(
    onOpenMediaDetail: (Uuid) -> Unit,
    onBack: () -> Unit,
    onNavigateToJournal: (Uuid) -> Unit,
) {
    routeEntry<LibraryListRoute>(
        metadata = HomeScene.homeScene(),
    ) { _ ->
        CompositionLocalProvider(
            FeatureSharedTransitionScope provides NavigationSharedTransitionScope.current,
            LocalNavAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
        ) {
            LibraryScreen(onOpenMediaDetail = onOpenMediaDetail)
        }
    }

    routeEntry<LibraryMediaDetailRoute> { route ->
        val context = LocalContext.current
        val navigationScope = NavigationSharedTransitionScope.current
        val animatedContentScope = LocalNavAnimatedContentScope.current

        val sharedBoundsModifier =
            if (navigationScope != null) {
                with(navigationScope) {
                    Modifier.sharedBounds(
                        rememberSharedContentState(
                            key = "$LIBRARY_MEDIA_TRANSITION_KEY-${route.mediaId}",
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
