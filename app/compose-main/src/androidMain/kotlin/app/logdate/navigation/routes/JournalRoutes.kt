package app.logdate.navigation.routes

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.journals.ui.JournalClickCallback
import app.logdate.feature.journals.ui.JournalsOverviewScreen
import app.logdate.feature.journals.ui.creation.JournalCreationScreen
import app.logdate.feature.journals.ui.detail.JournalDetailScreen
import app.logdate.feature.journals.ui.settings.JournalSettingsScreen
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.JournalDetail
import app.logdate.navigation.routes.core.JournalList
import app.logdate.navigation.routes.core.JournalSettings
import app.logdate.navigation.routes.core.NewJournalRoute
import app.logdate.navigation.routes.core.ShareJournal
import app.logdate.navigation.routes.routeEntry
import app.logdate.navigation.scenes.HomeScene
import kotlin.uuid.Uuid

fun MainAppNavigator.openJournalDetail(
    journalId: Uuid,
    replaceCurrentScreen: Boolean = false,
) {
    if (replaceCurrentScreen && backStack.size > 0) {
        // Replace the current screen with the journal detail
        backStack.removeLastOrNull()
    }

    backStack.add(
        JournalDetail(journalId),
    )
}

fun MainAppNavigator.openJournalSettings(journalId: Uuid) {
    backStack.add(
        JournalSettings(journalId),
    )
}

fun MainAppNavigator.openShareJournal(journalId: Uuid) {
    backStack.add(
        ShareJournal(journalId),
    )
}

/**
 * Handles navigation after journal creation is complete.
 * Removes the creation screen from backstack and navigates to journal detail.
 */
fun MainAppNavigator.finishJournalCreation(journalId: Uuid) {
    // Remove the journal creation screen first
    backStack.removeLastOrNull()

    // Then add the journal detail screen
    backStack.add(
        JournalDetail(journalId),
    )
}

/**
 * Provides the navigation routes for journal-related screens.
 */
fun EntryProviderScope<NavKey>.journalRoutes(
    onBack: () -> Unit,
    onOpenJournalDetail: JournalClickCallback,
    onCreateJournal: () -> Unit,
    onJournalDeleted: () -> Unit,
    onNavigateToNoteDetail: (Uuid) -> Unit,
    onNavigateToJournalSettings: (Uuid) -> Unit = {},
    onNavigateToShareJournal: (Uuid) -> Unit = {},
    onJournalCreated: (Uuid) -> Unit = {},
) {
    routeEntry<JournalList>(
        metadata = HomeScene.homeScene(), // Mark this as a home scene entry
    ) {
        JournalsOverviewScreen(
            onOpenJournal = onOpenJournalDetail,
            onBrowseJournals = { /* TODO: Handle browse navigation */ },
            onCreateJournal = onCreateJournal,
            onNavigationClick = { /* TODO: Handle navigation menu click */ },
        )
    }
    // Make sure we properly pass the journalId parameter to JournalDetailScreen
    routeEntry<JournalDetail> { route ->
        JournalDetailScreen(
            journalId = route.id,
            onGoBack = onBack,
            onJournalDeleted = onJournalDeleted,
            onNavigateToNoteDetail = onNavigateToNoteDetail,
            onNavigateToSettings = onNavigateToJournalSettings,
            onNavigateToShare = onNavigateToShareJournal,
        )
    }
    routeEntry<JournalSettings> { settings ->
        JournalSettingsScreen(
            journalId = settings.journalId,
            onGoBack = onBack,
            onJournalDeleted = onJournalDeleted,
        )
    }
    routeEntry<ShareJournal> { route ->
        app.logdate.feature.journals.ui.share.ShareJournalScreen(
            journalId = route.journalId.toString(),
            onGoBack = onBack,
        )
    }
    routeEntry<NewJournalRoute> { _ ->
        JournalCreationScreen(
            onGoBack = onBack,
            onJournalCreated = onJournalCreated,
        )
    }
}
