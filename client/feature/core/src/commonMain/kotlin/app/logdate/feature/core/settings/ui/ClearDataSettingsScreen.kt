@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.logdate.feature.core.settings.ui.dialogs.ClearDataConfirmationDialog
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.launch
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.clear_all_data
import logdate.client.feature.core.generated.resources.clear_data
import logdate.client.feature.core.generated.resources.clear_data_explanation
import logdate.client.feature.core.generated.resources.clear_data_failed
import logdate.client.feature.core.generated.resources.clear_data_success
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ClearDataSettingsScreen(
    onBack: () -> Unit,
    viewModel: DangerZoneSettingsViewModel = koinViewModel(),
) {
    ClearDataSettingsContent(
        onBack = onBack,
        onClearData = { onSuccess, onError -> viewModel.clearLocalData(onSuccess, onError) },
    )
}

@Composable
fun ClearDataSettingsContent(
    onBack: () -> Unit,
    onClearData: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val successMessage = stringResource(Res.string.clear_data_success)
    val failedMessage = stringResource(Res.string.clear_data_failed)
    var showDialog by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(Res.string.clear_all_data),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
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
                    text = stringResource(Res.string.clear_data_explanation),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { showDialog = true }) {
                    Text(stringResource(Res.string.clear_data))
                }
            }
        }
    }

    if (showDialog) {
        ClearDataConfirmationDialog(
            onDismissRequest = { showDialog = false },
            onConfirmation = {
                showDialog = false
                onClearData(
                    { scope.launch { snackbarHostState.showSnackbar(successMessage) } },
                    { _ -> scope.launch { snackbarHostState.showSnackbar(failedMessage) } },
                )
            },
        )
    }
}
