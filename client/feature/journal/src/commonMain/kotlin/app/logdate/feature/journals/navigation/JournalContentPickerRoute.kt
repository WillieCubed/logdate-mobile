package app.logdate.feature.journals.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.journals.ui.picker.JournalContentPickerScreen
import app.logdate.ui.navigation.taggedEntry
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class JournalContentPickerRoute(
    val journalId: String,
) : NavKey {
    constructor(journalId: Uuid) : this(journalId.toString())
}

/** Registers the existing-content picker as a nested journal route. */
fun EntryProviderScope<NavKey>.journalContentPickerEntry(onBack: () -> Unit) {
    taggedEntry<JournalContentPickerRoute> { route ->
        JournalContentPickerScreen(
            journalId = Uuid.parse(route.journalId),
            onBack = onBack,
        )
    }
}
