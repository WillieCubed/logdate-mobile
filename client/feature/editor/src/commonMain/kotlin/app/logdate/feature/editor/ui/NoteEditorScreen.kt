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
import org.koin.compose.viewmodel.koinViewModel

/**
 * The main screen for creating a new note.
 *
 * This is a wrapper around [EntryEditorContent] that handles navigation and initial setup.
 * It's the component that should be used in navigation destinations.
 * 
 * Note: Initial content should only be populated when explicitly provided through parameters.
 * By default, a new entry is truly new (empty) with no auto-loaded content.
 */
@Composable
fun NoteEditorScreen(
    onNavigateBack: () -> Unit,
    onEntrySaved: () -> Unit,
    modifier: Modifier = Modifier,
    initialTextContent: String? = null,
    attachments: List<String> = emptyList(),
    viewModel: EntryEditorViewModel = koinViewModel(),
) {
    // Set initial content ONLY when explicitly provided through parameters
    LaunchedEffect(initialTextContent, attachments) {
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

    // Render the EntryEditorScreen with our parameters
    // TODO: Figure out how to consolidate with screen this if needed
    EntryEditorContent(
        onNavigateBack = onNavigateBack,
        onEntrySaved = onEntrySaved,
        modifier = modifier,
        viewModel = viewModel
    )
}