@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Surfaces the in-app update banner above the navigation graph when Play has reported an
 * actionable state. Renders nothing when the controller is idle / checking / unsupported.
 *
 * The banner caps at 560dp so it doesn't span the entire content pane on tablets and
 * desktop windows.
 *
 * @param uiState current value of [AppUpdateController.uiState]
 * @param onLaunchUpdate invoked when the user taps "Update" on an [AppUpdateStatus.Available]
 *   state — typically calls back into [AppUpdateController.checkForUpdates] with a
 *   manual trigger so Play re-shows the flow
 * @param onCompleteUpdate invoked when the user taps "Restart" on an [AppUpdateStatus.Downloaded]
 *   state — typically calls back into [AppUpdateController.completeUpdate]
 */
@Composable
fun AppUpdatePrompt(
    uiState: AppUpdateUiState,
    onLaunchUpdate: () -> Unit,
    onCompleteUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message: String?
    val actionLabel: String?
    val onAction: (() -> Unit)?
    when (uiState.status) {
        AppUpdateStatus.Available -> {
            message = uiState.message ?: "An update is available."
            actionLabel = "Update"
            onAction = onLaunchUpdate
        }
        AppUpdateStatus.Downloaded -> {
            message = uiState.message ?: "Update downloaded. Restart to apply."
            actionLabel = "Restart"
            onAction = onCompleteUpdate
        }
        else -> {
            message = null
            actionLabel = null
            onAction = null
        }
    }
    if (message == null) return

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}
