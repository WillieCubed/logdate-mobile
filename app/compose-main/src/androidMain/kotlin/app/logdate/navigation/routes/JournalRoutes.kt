package app.logdate.navigation

import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import app.logdate.feature.journals.ui.JournalClickCallback
import app.logdate.feature.journals.ui.JournalsOverviewScreen
import app.logdate.feature.journals.ui.creation.JournalCreationScreen
import app.logdate.feature.journals.ui.detail.JournalDetailScreen
import app.logdate.feature.journals.ui.settings.JournalSettingsScreen
import kotlin.uuid.Uuid

fun MainAppNavigator.openJournalDetail(
    journalId: Uuid,
) {
    backStack.add(
        JournalDetail(journalId)
    )
}

fun MainAppNavigator.openJournalSettings(
    journalId: Uuid,
) {
    backStack.add(
        JournalSettings(journalId)
    )
}


/**
 * Provides the navigation routes for journal-related screens.
 */
fun EntryProviderBuilder<NavKey>.journalRoutes(
    onBack: () -> Unit,
    onOpenJournalDetail: JournalClickCallback,
    onCreateJournal: () -> Unit,
    onJournalDeleted: () -> Unit,
    onNavigateToNoteDetail: (Uuid, Uuid) -> Unit,
    onNavigateToJournalSettings: (Uuid) -> Unit = {},
) {
    entry<JournalList>(
        metadata = HomeScene.homeScene() // Mark this as a home scene entry
    ) {
        JournalsOverviewScreen(
            onOpenJournal = onOpenJournalDetail,
            onBrowseJournals = { /* TODO: Handle browse navigation */ },
            onCreateJournal = onCreateJournal
        )
    }
    entry<JournalDetail> { route ->
        JournalDetailScreen(
            onGoBack = onBack,
            onJournalDeleted = onJournalDeleted,
            onNavigateToNoteDetail = onNavigateToNoteDetail,
            onNavigateToSettings = onNavigateToJournalSettings,
        )
    }
    entry<JournalSettings> { settings ->
        JournalSettingsScreen(
            onGoBack = onBack,
        )
    }
    entry<NewJournalRoute> {
        JournalCreationScreen(
            onGoBack = onBack,
            onJournalCreated = onOpenJournalDetail
        )
    }
}