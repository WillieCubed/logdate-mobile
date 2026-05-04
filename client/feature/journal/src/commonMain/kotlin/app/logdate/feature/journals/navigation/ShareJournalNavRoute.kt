package app.logdate.feature.journals.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.journals.ui.share.ShareJournalScreen
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Route for the share-journal flow that shows shareable links and collaborator state for the
 * given journal.
 */
@Serializable
data class ShareJournalRoute(
    val journalId: String,
) : NavKey {
    constructor(journalId: Uuid) : this(journalId.toString())
}

fun NavController.navigateToShareJournal(journalId: Uuid) {
    navigate(ShareJournalRoute(journalId))
}

fun NavGraphBuilder.shareJournalRoute(onGoBack: () -> Unit) {
    composable<ShareJournalRoute> { entry ->
        val route = entry.toRoute<ShareJournalRoute>()
        ShareJournalScreen(
            journalId = route.journalId,
            onGoBack = onGoBack,
        )
    }
}
