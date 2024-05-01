package app.logdate.mobile.ui.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.logdate.feature.rewind.ui.detail.RewindDetailRoute
import app.logdate.feature.rewind.ui.past.PastRewindsRoute

const val REWIND_ROUTE: String = "rewind"

const val REWIND_ID_ARG = "rewindId"


fun NavController.navigateToRewindList() = navigate(REWIND_ROUTE)
fun NavController.navigateToRewindList(navOptions: NavOptions) = navigate(REWIND_ROUTE, navOptions)

fun NavController.navigateToRewind(rewindId: String, navOptions: NavOptions) =
    navigate("$REWIND_ROUTE/$rewindId", navOptions)

fun NavGraphBuilder.rewindRoute(
    onGoBack: () -> Unit,
    onOpenJournal: (journalId: String) -> Unit,
) {
    rewindListRoute(onGoBack = onGoBack)
    rewindDetailRoute(onGoBack = onGoBack)
}

fun NavGraphBuilder.rewindDetailRoute(
    onGoBack: () -> Unit,
) {
    composable(
        route = "$REWIND_ROUTE/{$REWIND_ID_ARG}",
        arguments = listOf(navArgument(REWIND_ID_ARG) { type = NavType.StringType }),
        enterTransition = {
            slideInHorizontally()
        },
        exitTransition = {
            slideOutHorizontally()
        },
    ) {
        RewindDetailRoute(
            onGoBack = onGoBack,
        )
    }
}

fun NavGraphBuilder.rewindListRoute(
    onGoBack: () -> Unit,
) {
    composable(
        route = REWIND_ROUTE,
        // TODO: Support deep link
    ) {
        PastRewindsRoute(
            onGoBack = onGoBack,
        )
    }
}