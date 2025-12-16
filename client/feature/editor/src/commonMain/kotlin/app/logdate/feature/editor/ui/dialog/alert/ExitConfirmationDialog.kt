package app.logdate.feature.editor.ui.dialog.alert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.logdate.ui.theme.Spacing

/**
 * Confirmation dialog that appears when a user tries to exit the editor with unsaved changes.
 * Provides options to cancel, save as draft, or exit without saving.
 *
 * @param onDismiss Called when the user cancels the dialog (continues editing)
 * @param onSaveAsDraft Called when the user wants to save the current content as a draft
 * @param onDiscardAndExit Called when the user wants to exit without saving
 * @param modifier Optional modifier for the dialog layout
 */
@Composable
fun ExitConfirmationDialog(
    onDismiss: () -> Unit,
    onSaveAsDraft: () -> Unit,
    onDiscardAndExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Unsaved Changes",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = "You have unsaved changes that will be lost if you exit now.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Spacer(modifier = Modifier.height(Spacing.md))
                
                Text(
                    text = "Would you like to save your changes as a draft?",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)
            ) {
                // Cancel button - continue editing
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                
                Spacer(modifier = Modifier.width(Spacing.sm))
                
                // Discard button - exit without saving
                OutlinedButton(onClick = onDiscardAndExit) {
                    Text("Discard")
                }
                
                // Save as draft button - primary action
                FilledTonalButton(onClick = onSaveAsDraft) {
                    Text("Save Draft")
                }
            }
        },
        // No dismiss button needed since we have a custom button layout
        dismissButton = null,
        modifier = modifier.padding(Spacing.md)
    )
}