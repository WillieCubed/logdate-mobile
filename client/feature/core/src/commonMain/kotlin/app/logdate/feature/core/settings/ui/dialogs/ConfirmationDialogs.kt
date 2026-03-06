@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.action_reset_app
import logdate.client.feature.core.generated.resources.cancel
import logdate.client.feature.core.generated.resources.clear_all_data_2
import logdate.client.feature.core.generated.resources.clear_data_acknowledgement
import logdate.client.feature.core.generated.resources.clear_data_warning_message
import logdate.client.feature.core.generated.resources.yes_clear_all_data
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
        message =
            "This action cannot be undone.\n\n" +
                "This will permanently delete all your journals, entries, photos, " +
                "settings, and account data on this device.\n\n" +
                "Before continuing, export and verify a backup from Data & Storage.",
        confirmButtonText = stringResource(Res.string.action_reset_app),
        icon = Icons.Default.WarningAmber,
        acknowledgementLabel = "I understand that resetting may permanently remove encrypted local data.",
    )
}

/**
 * Dialog for confirming clearing all local data.
 * Uses acknowledgement checkbox like [ResetAppConfirmationDialog] for consistency.
 */
@Composable
fun ClearDataConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
) {
    DangerConfirmationDialog(
        onDismissRequest = onDismissRequest,
        onConfirmation = onConfirmation,
        title = stringResource(Res.string.clear_all_data_2),
        message = stringResource(Res.string.clear_data_warning_message),
        confirmButtonText = stringResource(Res.string.yes_clear_all_data),
        icon = Icons.Default.WarningAmber,
        acknowledgementLabel = stringResource(Res.string.clear_data_acknowledgement),
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
    icon: ImageVector = Icons.Default.WarningAmber,
    acknowledgementLabel: String? = null,
) {
    val acknowledgementText = acknowledgementLabel?.takeUnless { it.isBlank() }
    val requiresAcknowledgement = acknowledgementText != null
    var isAcknowledged by remember(acknowledgementLabel) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.error,
            )
        },
        text = {
            Column {
                Text(text = message)
                if (acknowledgementText != null) {
                    Spacer(
                        modifier =
                            androidx.compose.ui.Modifier
                                .height(12.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isAcknowledged,
                            onCheckedChange = { isAcknowledged = it },
                        )
                        Text(acknowledgementText)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmation,
                enabled = !requiresAcknowledgement || isAcknowledged,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
