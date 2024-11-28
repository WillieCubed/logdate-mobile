package app.logdate.feature.journals.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.logdate.feature.journals.ui.JournalsOverviewScreen
import kotlinx.serialization.Serializable

/**
 * Route for the journals overview screen.
 */
@Serializable
data object JournalsOverviewRoute

/**
 * Navigates to the journals overview screen.
 */
fun NavController.navigateToJournalsOverview() {
    navigate(JournalsOverviewRoute)
}

/**
 * Defines the journals overview route.
 */
fun NavGraphBuilder.journalsOverviewRoute(
    onOpenJournal: (journalId: String) -> Unit,
) {
    composable<JournalsOverviewRoute> {
        JournalsOverviewScreen(
            onOpenJournal = onOpenJournal,
            onBrowseJournals = {},
        )
    }
}