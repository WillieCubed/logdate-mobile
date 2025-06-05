@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package app.logdate.feature.editor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import io.github.aakira.napier.Napier
import org.koin.compose.viewmodel.koinViewModel

/**
 * The main screen for creating a new note.
 *
 * This is a wrapper around [EntryEditorScreen] that handles navigation and initial setup.
 * It's the component that should be used in navigation destinations.
 */
@Composable
fun NoteEditorScreen(
    onNavigateBack: () -> Unit,
    onEntrySaved: () -> Unit,
    initialTextContent: String? = null,
    attachments: List<String> = emptyList(),
    viewModel: EntryEditorViewModel = koinViewModel(),
) {
    // Set initial content from parameters if provided
    LaunchedEffect(initialTextContent, attachments) {
        try {
            // Handle initial text content if provided
            if (!initialTextContent.isNullOrBlank()) {
                viewModel.setInitialTextContent(initialTextContent)
            }

            // Handle initial attachments if provided
            if (attachments.isNotEmpty()) {
                viewModel.setInitialAttachments(attachments)
            }
        } catch (e: Exception) {
            Napier.e("Failed to set initial content", e)
        }
    }

    // Render the EntryEditorScreen with our parameters
    EntryEditorScreen(
        onNavigateBack = onNavigateBack,
        onEntrySaved = onEntrySaved,
        viewModel = viewModel
    )
}