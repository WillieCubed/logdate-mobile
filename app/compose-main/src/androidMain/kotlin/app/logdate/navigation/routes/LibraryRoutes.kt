package app.logdate.navigation.routes

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.library.ui.LibraryScreen
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
        // Placeholder for media detail — will be implemented in Phase 5
        MediaDetailPlaceholder(mediaId = route.mediaId)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun MediaDetailPlaceholder(
    mediaId: Uuid,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "Media Detail: $mediaId",
        modifier = modifier,
    )
}
