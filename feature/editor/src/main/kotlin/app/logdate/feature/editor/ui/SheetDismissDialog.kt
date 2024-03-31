package app.logdate.feature.editor.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.logdate.feature.editor.R

/**
 * A dialog that informs the user if they have unsaved changes and allows them to dismiss the screen.
 */
@Composable
internal fun SheetDismissDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        title = { Text(stringResource(R.string.action_note_create_discard_confirmation_title)) },
        text = { Text(stringResource(R.string.action_note_create_discard_confirmation_description)) },
        onDismissRequest = onCancel,
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_note_create_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_note_create_discard))
            }
        },
    )
}