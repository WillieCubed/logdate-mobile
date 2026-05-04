package app.logdate.feature.editor.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.logdate.feature.editor.ui.NoteEditorScreen
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Typed route for the entry editor. Carries optional context so the screen can resume an
 * existing entry or draft, or pre-select journals for a brand-new entry.
 *
 * Strings rather than `Uuid`s because Navigation Compose's saved-state serialization on iOS
 * does not handle `Uuid` natively yet. The route extension parses them back into `Uuid` before
 * handing them to `NoteEditorScreen`.
 */
@Serializable
data class EntryEditorRoute(
    val entryId: String? = null,
    val draftId: String? = null,
    val journalIds: List<String> = emptyList(),
)

fun NavController.navigateToEditor(
    entryId: Uuid? = null,
    draftId: Uuid? = null,
    journalIds: List<Uuid> = emptyList(),
) = navigate(
    EntryEditorRoute(
        entryId = entryId?.toString(),
        draftId = draftId?.toString(),
        journalIds = journalIds.map { it.toString() },
    ),
)

fun NavGraphBuilder.editorRoute(
    onNavigateBack: () -> Unit,
    onEntrySaved: () -> Unit,
) {
    composable<EntryEditorRoute> { entry ->
        val route = entry.toRoute<EntryEditorRoute>()
        NoteEditorScreen(
            onNavigateBack = onNavigateBack,
            onEntrySaved = onEntrySaved,
            entryId = route.entryId?.let(Uuid::parse),
            draftId = route.draftId?.let(Uuid::parse),
            journalIds = route.journalIds.map(Uuid::parse),
        )
    }
}
