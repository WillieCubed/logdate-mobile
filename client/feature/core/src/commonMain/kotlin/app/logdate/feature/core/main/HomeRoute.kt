package app.logdate.feature.core.main

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.logdate.feature.journals.ui.JournalClickCallback
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data object HomeRoute

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
) {
    composable<HomeRoute> {
        // Removed problematic CompositionLocalProvider that was causing the Compose compiler bug
        HomeScreen(
            onNewEntry = onCreateNote,
            onOpenJournal = onOpenJournal,
            onCreateJournal = onCreateJournal,
            onOpenSettings = onOpenSettings,
        )
    }
}