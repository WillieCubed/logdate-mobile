package app.logdate.feature.core.settings.ui.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.action_reset_app
import org.jetbrains.compose.resources.stringResource

/**
 * Dialog for confirming application reset.
 * This shows a warning to the user about the consequences of resetting the app,
 * including loss of all data.
 *
 * @param onDismissRequest Callback for when the dialog is dismissed
 * @param onConfirmation Callback for when the user confirms the reset
 */
@Composable
fun ResetAppConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
) {
    DangerConfirmationDialog(
        onDismissRequest = onDismissRequest,
        onConfirmation = onConfirmation,
        title = "Reset Entire App?",
        message = "⚠️ This action cannot be undone.\n\n" +
                "This will permanently delete all your journals, entries, photos, " +
                "settings, and account data.\n\n" +
                "Are you absolutely sure you want to continue?",
        confirmButtonText = stringResource(Res.string.action_reset_app),
        icon = Icons.Default.WarningAmber
    )
}

/**
 * Generic danger confirmation dialog with customizable content.
 * Used for potentially destructive actions that require user confirmation.
 *
 * @param onDismissRequest Callback for when the dialog is dismissed
 * @param onConfirmation Callback for when the user confirms the action
 * @param title Title of the dialog
 * @param message Detailed warning message
 * @param confirmButtonText Text for the confirmation button
 * @param icon Icon to display in the dialog header
 */
@Composable
fun DangerConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    title: String,
    message: String,
    confirmButtonText: String,
    icon: ImageVector = Icons.Default.WarningAmber
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            Button(
                onClick = onConfirmation,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}