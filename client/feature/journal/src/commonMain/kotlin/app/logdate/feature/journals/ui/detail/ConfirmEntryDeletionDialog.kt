@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.journals.ui.detail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import logdate.client.feature.journal.generated.resources.*
import logdate.client.feature.journal.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * A dialog that asks the user to confirm deletion of a journal entry.
 */
@Composable
fun ConfirmEntryDeletionDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    icon: ImageVector = Icons.Default.WarningAmber,
) {
    AlertDialog(
        icon = {
            Icon(icon, contentDescription = null)
        },
        title = {
            Text(text = stringResource(Res.string.delete_entry_2))
        },
        text = {
            Text(text = stringResource(Res.string.are_you_sure_you_want_to_delete_this_entry_this_action_cannot_be_undone))
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onConfirmation,
            ) {
                Text(stringResource(Res.string.delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
            ) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
