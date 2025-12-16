@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.logdate.feature.journals.ui.JournalsOverviewScreen
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

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
    onOpenJournal: (journalId: Uuid) -> Unit,
    onCreateJournal: () -> Unit,
    onNavigationClick: () -> Unit = {},
) {
    composable<JournalsOverviewRoute> {
        CompositionLocalProvider(
            LocalNavAnimatedVisibilityScope provides this,
        ) {
            JournalsOverviewScreen(
                onOpenJournal = onOpenJournal,
                onBrowseJournals = {},
                onCreateJournal = onCreateJournal,
                onNavigationClick = onNavigationClick,
            )
        }
    }
}