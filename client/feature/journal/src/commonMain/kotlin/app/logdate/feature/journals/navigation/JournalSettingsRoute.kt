@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.logdate.feature.journals.ui.settings.JournalSettingsScreen
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Route for the journal settings screen.
 */
@Serializable
data class JournalSettingsRoute(val journalId: String) {
    constructor(journalId: Uuid) : this(journalId.toString())
}

/**
 * Navigates to the journal settings screen.
 *
 * @param journalId The ID of the journal to configure.
 */
fun NavController.navigateToJournalSettings(journalId: Uuid) {
    navigate(JournalSettingsRoute(journalId))
}

/**
 * Defines the journal settings route.
 *
 * @deprecated In favor of Navigation 3
 */
fun NavGraphBuilder.journalSettingsRoute(
    onGoBack: () -> Unit,
    onJournalDeleted: () -> Unit = {},
) {
    composable<JournalSettingsRoute>(
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
        val route = backStackEntry.savedStateHandle.toRoute<JournalSettingsRoute>()
        val journalId = Uuid.parse(route.journalId)
        
        CompositionLocalProvider(
            LocalNavAnimatedVisibilityScope provides this,
        ) {
            JournalSettingsScreen(
                journalId = journalId,
                onGoBack = onGoBack,
                onJournalDeleted = onJournalDeleted,
            )
        }
    }
}