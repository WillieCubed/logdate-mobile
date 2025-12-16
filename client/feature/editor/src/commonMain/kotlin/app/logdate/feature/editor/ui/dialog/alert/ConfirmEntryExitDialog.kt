package app.logdate.feature.editor.ui.dialog.alert

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.action_note_create_cancel
import logdate.client.feature.editor.generated.resources.action_note_create_discard
import logdate.client.feature.editor.generated.resources.action_note_create_discard_confirmation_description
import logdate.client.feature.editor.generated.resources.action_note_create_discard_confirmation_title
import org.jetbrains.compose.resources.stringResource

/**
 * A dialog that informs the user if they have unsaved changes and allows them to dismiss the screen.
 */
@Composable
internal fun ConfirmEntryExitDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    dialogType: ConfirmDialogType = ConfirmDialogType.EXIT_EDITOR,
) {
    val (title, description) = when (dialogType) {
        ConfirmDialogType.EXIT_EDITOR -> Pair(
            stringResource(Res.string.action_note_create_discard_confirmation_title),
            stringResource(Res.string.action_note_create_discard_confirmation_description)
        )
        ConfirmDialogType.DELETE_BLOCK -> Pair(
            "Delete Block",
            "Are you sure you want to delete this block? This action cannot be undone."
        )
    }
    
    AlertDialog(
        title = { Text(title) },
        text = { Text(description) },
        onDismissRequest = onCancel,
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.action_note_create_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(when (dialogType) {
                    ConfirmDialogType.EXIT_EDITOR -> stringResource(Res.string.action_note_create_discard)
                    ConfirmDialogType.DELETE_BLOCK -> "Delete"
                })
            }
        },
    )
}

/**
 * Types of confirmation dialogs for the editor
 */
enum class ConfirmDialogType {
    EXIT_EDITOR,
    DELETE_BLOCK
}