package app.logdate.feature.library.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.logdate.feature.library.ui.LibraryScreen
import app.logdate.feature.library.ui.detail.MediaDetailScreen
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Route for the Library overview screen (Navigation Compose 2).
 */
@Serializable
data object LibraryOverviewRoute

/**
 * Navigates to the Library overview screen.
 */
fun NavController.navigateToLibrary() {
    navigate(LibraryOverviewRoute)
}

/**
 * A route corresponding to the media detail screen.
 */
@Serializable
data class MediaDetailRoute(
    val id: String,
) {
    constructor(id: Uuid) : this(id.toString())
}

/**
 * Navigates to the media detail screen.
 */
fun NavController.navigateToMediaDetail(mediaId: Uuid) {
    navigate(MediaDetailRoute(mediaId))
}

/**
 * Registers the Library overview route in a NavGraphBuilder.
 */
fun NavGraphBuilder.libraryRoute(onOpenMediaDetail: (Uuid) -> Unit) {
    composable<LibraryOverviewRoute> {
        LibraryScreen(onOpenMediaDetail = onOpenMediaDetail)
    }
}

/**
 * Registers the media detail route in a NavGraphBuilder.
 */
fun NavGraphBuilder.mediaDetailRoute(onGoBack: () -> Unit) {
    composable<MediaDetailRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<MediaDetailRoute>()
        MediaDetailScreen(
            mediaId = Uuid.parse(route.id),
            onBack = onGoBack,
        )
    }
}
