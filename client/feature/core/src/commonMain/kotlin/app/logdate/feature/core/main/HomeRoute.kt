package app.logdate.feature.core.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.journals.ui.JournalClickCallback
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data object HomeRoute : NavKey

fun NavHostController.navigateHome() {
    // Ensure you can't go back
    navigate(HomeRoute) {
        popUpTo(HomeRoute) {
            inclusive = true
        }
    }
}

/**
 * Navigation graph for core top-level routes
 */
fun NavGraphBuilder.homeGraph(
    onCreateNote: () -> Unit,
    onOpenJournal: JournalClickCallback,
    onCreateJournal: () -> Unit,
    onOpenRewind: (uid: Uuid) -> Unit,
    onOpenSettings: () -> Unit,
    onBrowseJournals: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenDraft: (draftId: String) -> Unit = {},
    onImportBackup: () -> Unit = {},
    onOpenMediaDetail: (Uuid) -> Unit = {},
    libraryContent: @Composable (Modifier) -> Unit = {},
    locationContent: @Composable (Modifier) -> Unit = {},
) {
    composable<HomeRoute> {
        HomeScreen(
            onNewEntry = onCreateNote,
            onOpenJournal = onOpenJournal,
            onCreateJournal = onCreateJournal,
            onBrowseJournals = onBrowseJournals,
            onOpenRewind = onOpenRewind,
            onOpenSettings = onOpenSettings,
            onOpenSearch = onOpenSearch,
            onOpenDraft = onOpenDraft,
            onImportBackup = onImportBackup,
            onOpenMediaDetail = onOpenMediaDetail,
            libraryContent = libraryContent,
            locationContent = locationContent,
        )
    }
}
