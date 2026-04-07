package app.logdate.feature.editor.ui.dialog.alert

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.editor_action_discard
import logdate.client.feature.editor.generated.resources.editor_discard_dialog_description
import logdate.client.feature.editor.generated.resources.editor_discard_dialog_title
import logdate.client.feature.editor.generated.resources.save_draft
import logdate.client.ui.generated.resources.common_cancel
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

/**
 * A dialog that informs the user if they have unsaved changes and allows them to dismiss the screen.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun ConfirmEntryExitDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onSaveAsDraft: (() -> Unit)? = null,
    dialogType: ConfirmDialogType = ConfirmDialogType.EXIT_EDITOR,
) {
    val (title, description) =
        when (dialogType) {
            ConfirmDialogType.EXIT_EDITOR ->
                Pair(
                    stringResource(Res.string.editor_discard_dialog_title),
                    stringResource(Res.string.editor_discard_dialog_description),
                )
            ConfirmDialogType.DELETE_BLOCK ->
                Pair(
                    "Delete Block",
                    "Are you sure you want to delete this block? This action cannot be undone.",
                )
        }

    AlertDialog(
        title = { Text(title) },
        text = { Text(description) },
        onDismissRequest = onCancel,
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("exit_dialog_cancel"),
            ) {
                Text(stringResource(UiRes.string.common_cancel))
            }
        },
        confirmButton = {
            if (onSaveAsDraft != null && dialogType == ConfirmDialogType.EXIT_EDITOR) {
                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.testTag("exit_dialog_discard"),
                ) {
                    Text(stringResource(Res.string.editor_action_discard))
                }
                TextButton(
                    onClick = onSaveAsDraft,
                    modifier = Modifier.testTag("exit_dialog_save_draft"),
                ) {
                    Text(stringResource(Res.string.save_draft))
                }
            } else {
                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.testTag("exit_dialog_discard"),
                ) {
                    Text(
                        when (dialogType) {
                            ConfirmDialogType.EXIT_EDITOR -> stringResource(Res.string.editor_action_discard)
                            ConfirmDialogType.DELETE_BLOCK -> "Delete"
                        },
                    )
                }
            }
        },
    )
}

/**
 * Types of confirmation dialogs for the editor
 */
enum class ConfirmDialogType {
    EXIT_EDITOR,
    DELETE_BLOCK,
}
