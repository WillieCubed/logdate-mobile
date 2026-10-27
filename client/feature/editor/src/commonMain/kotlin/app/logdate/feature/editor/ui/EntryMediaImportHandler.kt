package app.logdate.feature.editor.ui

import androidx.compose.material3.SnackbarHostState
import app.logdate.feature.editor.ui.drop.EntryMediaImport
import app.logdate.feature.editor.ui.drop.MediaImportProblem
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.media_import_unreadable
import logdate.client.feature.editor.generated.resources.media_import_unsupported
import org.jetbrains.compose.resources.getString

internal fun entryMediaImportHandler(
    viewModel: EntryEditorViewModel,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
): (EntryMediaImport) -> Unit =
    { result ->
        viewModel.setInitialAttachments(result.uris)
        result.problems.firstOrNull()?.let { problem ->
            scope.launch {
                val message =
                    when (problem) {
                        MediaImportProblem.Unsupported -> Res.string.media_import_unsupported
                        MediaImportProblem.Unreadable -> Res.string.media_import_unreadable
                    }
                snackbar.showSnackbar(getString(message))
            }
        }
    }
