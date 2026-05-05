package app.logdate.feature.journals.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.journals.ui.JournalsOverviewScreen
import app.logdate.ui.navigation.taggedEntry
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data object JournalsOverviewRoute : NavKey

/** Pushes the journals overview list. */
fun NavBackStack<NavKey>.navigateToJournalsOverview() {
    add(JournalsOverviewRoute)
}

/** Registers the journals overview entry. */
fun EntryProviderScope<NavKey>.journalsOverviewEntry(
    onOpenJournal: (Uuid) -> Unit,
    onCreateJournal: () -> Unit,
    onBrowseJournals: () -> Unit = {},
) {
    taggedEntry<JournalsOverviewRoute> {
        JournalsOverviewScreen(
            onOpenJournal = onOpenJournal,
            onBrowseJournals = onBrowseJournals,
            onCreateJournal = onCreateJournal,
        )
    }
}

/**
 * Convenience that registers every journal-related entry: overview, detail, settings,
 * creation, share, and note viewer. Callers supply navigation lambdas; this module never
 * has to know about the surrounding back stack shape.
 */
fun EntryProviderScope<NavKey>.journalEntries(
    onOpenJournal: (Uuid) -> Unit,
    onCreateJournal: () -> Unit,
    onBack: () -> Unit,
    onJournalDeleted: () -> Unit,
    onOpenNote: (Uuid) -> Unit,
    onOpenEditorForJournal: (Uuid) -> Unit,
    onOpenJournalSettings: (Uuid) -> Unit,
    onShareJournal: (Uuid) -> Unit,
) {
    journalsOverviewEntry(
        onOpenJournal = onOpenJournal,
        onCreateJournal = onCreateJournal,
    )
    journalDetailsEntry(
        onBack = onBack,
        onJournalDeleted = onJournalDeleted,
        onOpenNote = onOpenNote,
        onOpenEditor = onOpenEditorForJournal,
        onOpenSettings = onOpenJournalSettings,
        onShareJournal = onShareJournal,
    )
    journalCreationEntry(
        onBack = onBack,
        onJournalCreated = onOpenJournal,
    )
    journalSettingsEntry(
        onBack = onBack,
        onJournalDeleted = onJournalDeleted,
    )
    shareJournalEntry(onBack = onBack)
    noteDetailEntry(onBack = onBack)
}
