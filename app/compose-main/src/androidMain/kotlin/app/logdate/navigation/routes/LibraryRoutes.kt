package app.logdate.navigation.routes

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import app.logdate.feature.library.ui.LibraryScreen
import app.logdate.feature.library.ui.detail.MediaDetailScreen
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.LibraryListRoute
import app.logdate.navigation.routes.core.LibraryMediaDetailRoute
import app.logdate.navigation.scenes.HomeScene
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import kotlin.uuid.Uuid
import app.logdate.navigation.LocalSharedTransitionScope as NavigationSharedTransitionScope
import app.logdate.ui.LocalSharedTransitionScope as FeatureSharedTransitionScope

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
        CompositionLocalProvider(
            FeatureSharedTransitionScope provides NavigationSharedTransitionScope.current,
            LocalNavAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
        ) {
            MediaDetailScreen(
                mediaId = route.mediaId,
                onBack = onBack,
                onNavigateToJournal = onNavigateToJournal,
                onShare = { mediaUri ->
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(mediaUri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(Intent.createChooser(shareIntent, null))
                },
            )
        }
    }
}
