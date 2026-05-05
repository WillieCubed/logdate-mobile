package app.logdate.feature.journals.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.journals.ui.detail.JournalDetailScreen
import app.logdate.ui.navigation.taggedEntry
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class JournalDetailsRoute(
    val journalId: String,
) : NavKey {
    constructor(journalId: Uuid) : this(journalId.toString())
}

/** Pushes the journal detail screen for the given id. */
fun NavBackStack<NavKey>.navigateToJournal(journalId: Uuid) {
    add(JournalDetailsRoute(journalId))
}

/** Registers the journal detail entry. */
fun EntryProviderScope<NavKey>.journalDetailsEntry(
    onBack: () -> Unit,
    onJournalDeleted: () -> Unit,
    onOpenNote: (Uuid) -> Unit,
    onOpenEditor: (Uuid) -> Unit = {},
    onOpenSettings: (Uuid) -> Unit = {},
    onShareJournal: (Uuid) -> Unit = {},
) {
    taggedEntry<JournalDetailsRoute> { route ->
        JournalDetailScreen(
            journalId = Uuid.parse(route.journalId),
            onGoBack = onBack,
            onJournalDeleted = onJournalDeleted,
            onNavigateToNoteDetail = onOpenNote,
            onOpenEditor = onOpenEditor,
            onNavigateToSettings = onOpenSettings,
            onNavigateToShare = onShareJournal,
        )
    }
}
