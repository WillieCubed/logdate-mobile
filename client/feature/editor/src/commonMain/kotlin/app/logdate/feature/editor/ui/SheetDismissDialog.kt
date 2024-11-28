package app.logdate.feature.editor.ui

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
internal fun SheetDismissDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        title = { Text(stringResource(Res.string.action_note_create_discard_confirmation_title)) },
        text = { Text(stringResource(Res.string.action_note_create_discard_confirmation_description)) },
        onDismissRequest = onCancel,
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.action_note_create_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.action_note_create_discard))
            }
        },
    )
}