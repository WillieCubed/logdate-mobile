package app.logdate.navigation.routes

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.library.ui.LibraryScreen
import app.logdate.feature.library.ui.detail.MediaDetailScreen
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.LibraryListRoute
import app.logdate.navigation.routes.core.LibraryMediaDetailRoute
import app.logdate.navigation.scenes.HomeScene
import kotlin.uuid.Uuid

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
        LibraryScreen(onOpenMediaDetail = onOpenMediaDetail)
    }

    routeEntry<LibraryMediaDetailRoute> { route ->
        MediaDetailScreen(
            mediaId = route.mediaId,
            onBack = onBack,
            onNavigateToJournal = onNavigateToJournal,
        )
    }
}
