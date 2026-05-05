package app.logdate.feature.journals.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.journals.ui.detail.NoteViewerScreen
import app.logdate.ui.navigation.taggedEntry
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class NoteDetailRoute(
    val noteId: String,
) : NavKey {
    constructor(noteId: Uuid) : this(noteId.toString())
}

/** Pushes the note viewer for the given note id. */
fun NavBackStack<NavKey>.navigateToNoteDetail(noteId: Uuid) {
    add(NoteDetailRoute(noteId))
}

/** Registers the note viewer entry. */
fun EntryProviderScope<NavKey>.noteDetailEntry(onBack: () -> Unit) {
    taggedEntry<NoteDetailRoute> { route ->
        NoteViewerScreen(
            noteId = Uuid.parse(route.noteId),
            onGoBack = onBack,
        )
    }
}
