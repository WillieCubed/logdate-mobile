@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.editor_entry_changed
import logdate.client.feature.editor.generated.resources.editor_save_separately
import org.jetbrains.compose.resources.getString

@Composable
internal fun EntryEditConflictFeedback(
    conflict: Boolean,
    snackbar: SnackbarHostState,
    saveSeparately: () -> Unit,
    dismiss: () -> Unit,
) {
    LaunchedEffect(conflict) {
        if (conflict) {
            val result =
                snackbar.showSnackbar(
                    message = getString(Res.string.editor_entry_changed),
                    actionLabel = getString(Res.string.editor_save_separately),
                    withDismissAction = true,
                    duration = SnackbarDuration.Indefinite,
                )
            if (result == SnackbarResult.ActionPerformed) saveSeparately() else dismiss()
        }
    }
}
