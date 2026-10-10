package app.logdate.feature.journals.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.client.repository.journals.JournalMergeOperation
import app.logdate.feature.journals.ui.merge.JournalMergeScreen
import app.logdate.ui.navigation.taggedEntry
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class JournalMergeRoute(
    val sourceId: String,
    val pendingOperationId: String? = null,
) : NavKey

fun EntryProviderScope<NavKey>.journalMergeEntry(
    onBack: () -> Unit,
    onJournalMerged: (JournalMergeOperation) -> Unit,
) {
    taggedEntry<JournalMergeRoute> { route ->
        JournalMergeScreen(
            sourceId = Uuid.parse(route.sourceId),
            pendingOperationId = route.pendingOperationId?.let(Uuid::parse),
            onBack = onBack,
            onJournalMerged = onJournalMerged,
        )
    }
}

fun MutableList<NavKey>.completeJournalMerge(operation: JournalMergeOperation) {
    removeAll { route ->
        when (route) {
            is JournalDetailsRoute ->
                route.journalId == operation.sourceId.toString() ||
                    route.journalId == operation.destinationId.toString()
            is JournalSettingsRoute -> route.journalId == operation.sourceId.toString()
            is ShareJournalRoute -> route.journalId == operation.sourceId.toString()
            is JournalContentPickerRoute -> route.journalId == operation.sourceId.toString()
            is JournalMergeRoute -> route.sourceId == operation.sourceId.toString()
            else -> false
        }
    }
    add(JournalDetailsRoute(operation.destinationId.toString(), mergedSourceTitle = operation.sourceTitle))
}
