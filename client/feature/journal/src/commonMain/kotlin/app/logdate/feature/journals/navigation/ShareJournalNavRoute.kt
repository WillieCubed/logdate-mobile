package app.logdate.feature.journals.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.journals.ui.share.ShareJournalScreen
import app.logdate.ui.navigation.taggedEntry
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

/** Pushes the share-journal flow for the given journal. */
fun NavBackStack<NavKey>.navigateToShareJournal(journalId: Uuid) {
    add(ShareJournalRoute(journalId))
}

/** Registers the share-journal entry. */
fun EntryProviderScope<NavKey>.shareJournalEntry(onBack: () -> Unit) {
    taggedEntry<ShareJournalRoute> { route ->
        ShareJournalScreen(
            journalId = route.journalId,
            onGoBack = onBack,
        )
    }
}
