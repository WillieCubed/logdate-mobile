@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.logdate.feature.journals.ui.detail.NoteViewerScreen
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Route for viewing a note by ID.
 */
@Serializable
data class NoteDetailRoute(val noteId: String) {
    constructor(noteId: Uuid) : this(noteId.toString())
}

/**
 * Navigation helper for opening a note by ID.
 *
 * @param noteId The ID of the note to navigate to.
 */
fun NavController.navigateToNoteDetail(noteId: Uuid) {
    navigate(NoteDetailRoute(noteId))
}

/**
 * Navigation entry for note viewing.
 */
fun NavGraphBuilder.noteDetailRoute(
    onGoBack: () -> Unit,
) {
    composable<NoteDetailRoute>(
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
            )
        }
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<NoteDetailRoute>()
        val noteId = Uuid.parse(route.noteId)
        CompositionLocalProvider(
            LocalNavAnimatedVisibilityScope provides this,
        ) {
            NoteViewerScreen(
                noteId = noteId,
                onGoBack = onGoBack,
            )
        }
    }
}
