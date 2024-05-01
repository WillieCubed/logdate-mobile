package app.logdate.feature.editor.navigation

import android.content.Intent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import app.logdate.feature.editor.ui.NewNoteRoute

const val ROUTE_NEW_NOTE = "main/new_note"

fun NavController.navigateToNoteCreation(navOptions: NavOptions) =
    navigate(ROUTE_NEW_NOTE, navOptions)

fun NavController.navigateToNoteCreation() = navigate(ROUTE_NEW_NOTE)

fun NavGraphBuilder.noteCreationRoute(
    onClose: () -> Unit,
    onNoteSaved: () -> Unit,
) {
    composable(
        route = ROUTE_NEW_NOTE,
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
        deepLinks = listOf(
            navDeepLink {
                action = Intent.ACTION_SEND
                mimeType = "text/*"
            },
            navDeepLink {
                action = Intent.ACTION_SEND
                mimeType = "image/*"
            },
            navDeepLink {
                action = Intent.ACTION_SEND_MULTIPLE
                mimeType = "image/*"
            },
        ),
    ) {
        NewNoteRoute(
            onClose = onClose,
            onNoteSaved = onNoteSaved,
        )
    }
}

