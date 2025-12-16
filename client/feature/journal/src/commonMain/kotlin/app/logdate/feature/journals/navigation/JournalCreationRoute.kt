package app.logdate.feature.journals.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.logdate.feature.journals.ui.creation.JournalCreationScreen
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Route for creating a new journal.
 */
@Serializable
data class JournalCreationRoute(
    val journalTitle: String = "",
)

/**
 * Navigates to the journal creation screen.
 */
fun NavController.navigateToJournalCreation() {
    navigate(JournalCreationRoute())
}

/**
 * Defines the navigation graph for creating a new journal.
 */
fun NavGraphBuilder.newJournalRoute(
    onGoBack: () -> Unit,
    onCreateJournal: (journalId: Uuid) -> Unit,
) {
    composable<JournalCreationRoute>(
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
    ) {
        JournalCreationScreen(
            onGoBack = onGoBack,
            onJournalCreated = onCreateJournal,
        )
    }
}
