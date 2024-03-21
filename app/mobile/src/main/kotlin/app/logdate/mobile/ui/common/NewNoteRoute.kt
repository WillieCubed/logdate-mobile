package app.logdate.mobile.ui.common

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import app.logdate.feature.editor.ui.NewNoteRoute
import app.logdate.mobile.ui.navigation.RouteDestination


fun NavController.navigateToNoteCreation(navOptions: NavOptions) =
    navigate(RouteDestination.NewNote.route, navOptions)

fun NavController.navigateToNoteCreation() = navigate(
    RouteDestination.NewNote.route,
)

fun NavGraphBuilder.noteCreationRoute(
    onClose: () -> Unit,
) {
    composable(
        route = RouteDestination.NewNote.route,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Up,
                animationSpec = tween(400),
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Down,
                animationSpec = tween(200),
            )
        },
        // TODO: Support deep link with prefilled content
    ) {
        NewNoteRoute(onClose = onClose)
    }
}
