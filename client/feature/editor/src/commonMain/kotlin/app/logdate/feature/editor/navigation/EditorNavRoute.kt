package app.logdate.feature.editor.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.editor.ui.NoteEditorScreen
import app.logdate.ui.navigation.taggedEntry
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Typed route for the entry editor. Carries optional context so the screen can resume an
 * existing entry or draft, or pre-select journals for a brand-new entry.
 *
 * Strings rather than `Uuid`s because Navigation 3's saved-state serialization on iOS does
 * not handle `Uuid` natively yet.
 */
@Serializable
data class EntryEditorRoute(
    val entryId: String? = null,
    val draftId: String? = null,
    val journalIds: List<String> = emptyList(),
) : NavKey

/** Pushes the editor onto the back stack with optional entry / draft / journal context. */
fun NavBackStack<NavKey>.navigateToEditor(
    entryId: Uuid? = null,
    draftId: Uuid? = null,
    journalIds: List<Uuid> = emptyList(),
) {
    add(
        EntryEditorRoute(
            entryId = entryId?.toString(),
            draftId = draftId?.toString(),
            journalIds = journalIds.map { it.toString() },
        ),
    )
}

/**
 * Registers the editor entry. The hosting graph supplies callbacks for back / save so the
 * editor module never has to know about the surrounding back stack shape.
 */
fun EntryProviderScope<NavKey>.editorEntry(
    onNavigateBack: () -> Unit,
    onEntrySaved: () -> Unit,
) {
    taggedEntry<EntryEditorRoute> { route ->
        NoteEditorScreen(
            onNavigateBack = onNavigateBack,
            onEntrySaved = onEntrySaved,
            entryId = route.entryId?.let(Uuid::parse),
            draftId = route.draftId?.let(Uuid::parse),
            journalIds = route.journalIds.map(Uuid::parse),
        )
    }
}
