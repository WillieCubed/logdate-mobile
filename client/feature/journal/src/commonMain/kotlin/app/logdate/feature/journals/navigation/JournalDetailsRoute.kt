@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.logdate.feature.journals.ui.detail.JournalDetailScreen
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Route for viewing a journal's details.
 */
@Deprecated("Use Navigation 3 when possible.")
@Serializable
data class JournalDetailsRoute(val journalId: String) {
    constructor(journalId: Uuid) : this(journalId.toString())
}


/**
 * Navigates to the journal detail screen.
 *
 * @param journalId The ID of the journal to navigate to.
 */
fun NavController.navigateToJournal(journalId: String) {
    navigate(JournalDetailsRoute(journalId))
}
/**
 * Navigates to the journal detail screen.
 *
 * @param journalId The ID of the journal to navigate to.
 */
fun NavController.navigateToJournal(journalId: Uuid) {
    navigate(JournalDetailsRoute(journalId))
}

/**
 * Navigates to the new journal screen after creating a new journal.
 *
 * This pops the back stack to avoid going back to the new journal screen.
 */
fun NavController.navigateToJournalFromNew(journalId: Uuid) {
    navigate(JournalDetailsRoute(journalId)) {
        popUpTo(JournalDetailsRoute) { inclusive = true }
    }
}


/**
 * Defines the journal details route.
 *
 */
@Deprecated("Use Navigation 3 when possible.")
fun NavGraphBuilder.journalDetailsRoute(
    onGoBack: () -> Unit,
    onJournalDeleted: () -> Unit,
    onNavigateToNoteDetail: (noteId: Uuid, journalId: Uuid) -> Unit,
) {
    composable<JournalDetailsRoute>(
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
            // Parse the journalId from the route
            val journalIdString = backStackEntry.arguments?.getString("journalId") ?: ""
            JournalDetailScreen(
                journalId = Uuid.parse(journalIdString),
                onGoBack = onGoBack,
                onJournalDeleted = onJournalDeleted,
                onNavigateToNoteDetail = onNavigateToNoteDetail,
            )
        }
    }
}
