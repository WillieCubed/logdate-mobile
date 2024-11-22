package app.logdate.feature.library.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import app.logdate.feature.library.ui.LibraryScreen

const val LIBRARY_ROUTE = "library"

fun NavController.navigateToLibrary(navOptions: NavOptions) = navigate(LIBRARY_ROUTE, navOptions)

fun NavGraphBuilder.libraryRoute(
    onGoToItem: (id: String) -> Unit,
) {
    composable(
        route = LIBRARY_ROUTE,
        // TODO: Support deep link
    ) {
        LibraryScreen(
            onGoToItem = onGoToItem,
        )
    }
}