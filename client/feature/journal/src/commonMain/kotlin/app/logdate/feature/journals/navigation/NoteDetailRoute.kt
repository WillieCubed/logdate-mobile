@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.logdate.feature.journals.ui.detail.NoteDetailScreen
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Route for viewing a note's details.
 */
@Serializable
data class NoteDetailRoute(val noteId: String, val journalId: String) {
    constructor(noteId: Uuid, journalId: Uuid) : this(noteId.toString(), journalId.toString())
}

/**
 * Navigates to the note detail screen.
 *
 * @param noteId The ID of the note to navigate to.
 * @param journalId The ID of the journal containing the note.
 */
fun NavController.navigateToNoteDetail(noteId: Uuid, journalId: Uuid) {
    navigate(NoteDetailRoute(noteId, journalId))
}

/**
 * Defines the note detail route.
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
        CompositionLocalProvider(
            LocalNavAnimatedVisibilityScope provides this,
        ) {
            NoteDetailScreen(
                onGoBack = onGoBack,
            )
        }
    }
}