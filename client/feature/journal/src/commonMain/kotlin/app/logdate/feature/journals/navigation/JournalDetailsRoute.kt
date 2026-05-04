@file:OptIn(ExperimentalSharedTransitionApi::class)
@file:Suppress("DEPRECATION")

package app.logdate.feature.journals.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.logdate.feature.journals.ui.detail.JournalDetailScreen
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Route for viewing a journal's details.
 */
@Deprecated("Use Navigation 3 when possible.")
@Serializable
data class JournalDetailsRoute(
    val journalId: String,
) : androidx.navigation3.runtime.NavKey {
    constructor(journalId: Uuid) : this(journalId.toString())
}

/**
 * Navigation helper for the journal detail screen.
 *
 * @param journalId The ID of the journal to navigate to.
 */
fun NavController.navigateToJournal(journalId: String) {
    navigate(JournalDetailsRoute(journalId))
}

/**
 * Navigation helper for the journal detail screen.
 *
 * @param journalId The ID of the journal to navigate to.
 */
fun NavController.navigateToJournal(journalId: Uuid) {
    navigate(JournalDetailsRoute(journalId))
}

/**
 * Navigation helper for the new journal completion flow.
 *
 * This pops the back stack to avoid returning to the new journal screen.
 */
fun NavController.navigateToJournalFromNew(journalId: Uuid) {
    navigate(JournalDetailsRoute(journalId)) {
        popUpTo(JournalDetailsRoute) { inclusive = true }
    }
}

/**
 * Navigation entry for journal details.
 */
@Deprecated("Use Navigation 3 when possible.")
fun NavGraphBuilder.journalDetailsRoute(
    onGoBack: () -> Unit,
    onJournalDeleted: () -> Unit,
    onNavigateToNoteDetail: (noteId: Uuid) -> Unit,
    onOpenEditor: (Uuid) -> Unit = {},
    onNavigateToSettings: (journalId: Uuid) -> Unit = {},
    onNavigateToShare: (journalId: Uuid) -> Unit = {},
) {
    composable<JournalDetailsRoute>(
        enterTransition = legacyJournalForwardEnterTransition,
        exitTransition = legacyJournalForwardExitTransition,
        popEnterTransition = legacyJournalPopEnterTransition,
        popExitTransition = legacyJournalPopExitTransition,
    ) { backStackEntry ->
        CompositionLocalProvider(
            LocalNavAnimatedVisibilityScope provides this,
        ) {
            val route = backStackEntry.toRoute<JournalDetailsRoute>()
            val journalIdString = route.journalId
            JournalDetailScreen(
                journalId = Uuid.parse(journalIdString),
                onGoBack = onGoBack,
                onJournalDeleted = onJournalDeleted,
                onNavigateToNoteDetail = onNavigateToNoteDetail,
                onOpenEditor = onOpenEditor,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToShare = onNavigateToShare,
            )
        }
    }
}
