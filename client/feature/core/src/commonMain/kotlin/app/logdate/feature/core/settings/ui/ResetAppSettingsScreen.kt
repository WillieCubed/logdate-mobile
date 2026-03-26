@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.logdate.feature.core.settings.ui.dialogs.ResetAppConfirmationDialog
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.action_reset_app
import logdate.client.feature.core.generated.resources.reset_app_explanation
import logdate.client.feature.core.generated.resources.settings_reset_app_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ResetAppSettingsScreen(
    onBack: () -> Unit,
    onAppReset: () -> Unit,
    viewModel: DangerZoneSettingsViewModel = koinViewModel(),
) {
    ResetAppSettingsContent(
        onBack = onBack,
        onAppReset = { viewModel.resetApp { onAppReset() } },
    )
}

@Composable
fun ResetAppSettingsContent(
    onBack: () -> Unit,
    onAppReset: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(Res.string.settings_reset_app_title),
        onBack = onBack,
    ) {
        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    text = stringResource(Res.string.reset_app_explanation),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { showDialog = true }) {
                    Text(stringResource(Res.string.action_reset_app))
                }
            }
        }
    }

    if (showDialog) {
        ResetAppConfirmationDialog(
            onDismissRequest = { showDialog = false },
            onConfirmation = {
                onAppReset()
                showDialog = false
            },
        )
    }
}
