package app.logdate.feature.library.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.logdate.feature.library.ui.LibraryScreen
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
 * Registers the Library overview route in a NavGraphBuilder.
 */
fun NavGraphBuilder.libraryRoute(onOpenMediaDetail: (Uuid) -> Unit) {
    composable<LibraryOverviewRoute> {
        LibraryScreen(onOpenMediaDetail = onOpenMediaDetail)
    }
}
