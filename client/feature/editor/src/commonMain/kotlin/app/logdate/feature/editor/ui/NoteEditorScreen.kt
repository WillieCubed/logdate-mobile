@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package app.logdate.feature.editor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import io.github.aakira.napier.Napier
import kotlin.uuid.Uuid
import org.koin.compose.viewmodel.koinViewModel

/**
 * The main screen for creating a new note or editing an existing entry.
 *
 * This is a wrapper around [EntryEditorContent] that handles navigation and initial setup.
 * It's the component that should be used in navigation destinations.
 *
 * When editing an existing entry, pass the entryId parameter to load and edit that entry.
 * When creating a new entry, leave entryId as null and optionally provide initial content
 * through initialTextContent and attachments parameters.
 *
 * Note: Initial content should only be populated when explicitly provided through parameters.
 * By default, a new entry is truly new (empty) with no auto-loaded content.
 *
 * @param onNavigateBack Callback invoked when the user navigates back from the editor (e.g., pressing back button)
 * @param onEntrySaved Callback invoked when the user successfully saves an entry
 * @param modifier Optional Compose modifier for customizing the screen's layout and appearance
 * @param entryId Optional unique identifier of an existing entry to load and edit. If provided, the entry
 *        is fetched and displayed for editing. If null, a new blank entry is created.
 * @param journalId Optional journal ID providing context for the entry. Used when editing an existing entry
 *        or to set the default journal for a new entry.
 * @param initialTextContent Optional initial text content to populate a new entry with. Only used when
 *        entryId is null (creating new entries). Ignored when editing existing entries.
 * @param attachments Optional list of attachment URIs (images, audio, video) to add to a new entry.
 *        Only used when entryId is null. Ignored when editing existing entries.
 * @param viewModel The ViewModel managing editor state and operations. Typically injected via Koin.
 */
@Composable
fun NoteEditorScreen(
    onNavigateBack: () -> Unit,
    onEntrySaved: () -> Unit,
    modifier: Modifier = Modifier,
    entryId: Uuid? = null,
    journalId: Uuid? = null,
    initialTextContent: String? = null,
    attachments: List<String> = emptyList(),
    viewModel: EntryEditorViewModel = koinViewModel(),
) {
    // Load existing entry if ID is provided
    LaunchedEffect(entryId) {
        entryId?.let {
            try {
                Napier.d("NoteEditorScreen: Loading existing entry: $it")
                viewModel.loadExistingEntry(it, journalId)
            } catch (e: Exception) {
                Napier.e("Failed to load existing entry: $entryId", e)
            }
        }
    }

    // Set initial content ONLY when creating a new entry (entryId is null)
    LaunchedEffect(initialTextContent, attachments, entryId) {
        // Only process initial content for new entries
        if (entryId == null) {
            try {
                // Handle initial text content if explicitly provided
                if (!initialTextContent.isNullOrBlank()) {
                    Napier.d("NoteEditorScreen: Setting initial text content: '${initialTextContent.take(20)}${if (initialTextContent.length > 20) "..." else ""}'")
                    viewModel.setInitialTextContent(initialTextContent)
                }

                // Handle initial attachments if explicitly provided
                if (attachments.isNotEmpty()) {
                    Napier.d("NoteEditorScreen: Setting ${attachments.size} initial attachments")
                    viewModel.setInitialAttachments(attachments)
                }
            } catch (e: Exception) {
                Napier.e("Failed to set initial content", e)
            }
        }
    }

    // Render the EntryEditorScreen with our parameters
    // TODO: Figure out how to consolidate with screen this if needed
    EntryEditorContent(
        onNavigateBack = onNavigateBack,
        onEntrySaved = onEntrySaved,
        modifier = modifier,
        viewModel = viewModel
    )
}