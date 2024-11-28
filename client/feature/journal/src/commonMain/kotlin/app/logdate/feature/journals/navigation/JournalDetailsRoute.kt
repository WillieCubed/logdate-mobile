package app.logdate.feature.journals.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.logdate.feature.journals.ui.detail.JournalDetailScreen
import kotlinx.serialization.Serializable

/**
 * Route for viewing a journal's details.
 */
@Serializable
data class JournalDetailsRoute(val journalId: String)


/**
 * Navigates to the journal detail screen.
 *
 * @param journalId The ID of the journal to navigate to.
 */
fun NavController.navigateToJournal(journalId: String) {
    navigate(JournalDetailsRoute(journalId))
}

/**
 * Navigates to the new journal screen after creating a new journal.
 *
 * This pops the back stack to avoid going back to the new journal screen.
 */
fun NavController.navigateToJournalFromNew(journalId: String) {
    navigate(JournalDetailsRoute(journalId)) {
        popUpTo(JournalDetailsRoute) { inclusive = true }
    }
}


/**
 * Defines the journal details route.
 */
fun NavGraphBuilder.journalDetailsRoute(
    onGoBack: () -> Unit,
    onJournalCreated: (journalId: String) -> Unit,
    onJournalDeleted: () -> Unit,
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
    ) { // journalDetailRoute
        JournalDetailScreen(
            onGoBack = onGoBack,
            onJournalDeleted = onJournalDeleted,
        )
    }
}
