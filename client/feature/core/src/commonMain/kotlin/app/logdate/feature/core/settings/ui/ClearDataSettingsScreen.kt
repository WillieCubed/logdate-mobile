@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.settings.ui.dialogs.ClearDataConfirmationDialog
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.launch
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_data_clear_action
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

    Box(modifier = Modifier.fillMaxSize()) {
        FoldableBookLayout(
            modifier = Modifier.fillMaxSize(),
            minPaneWidth = 320.dp,
            startPane = {
                ClearDataIntroPane(modifier = Modifier.fillMaxSize())
            },
            endPane = {
                ClearDataActionPane(modifier = Modifier.fillMaxSize()) { showDialog = true }
            },
            standardContent = {
                ClearDataCompactContent(
                    onBack = onBack,
                    onClearDataClicked = { showDialog = true },
                )
            },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Spacing.lg),
        )
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

@Composable
private fun ClearDataIntroPane(modifier: Modifier = Modifier) {
    ClearDataDetails(
        modifier = modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg),
    )
}

@Composable
private fun ClearDataActionPane(
    modifier: Modifier = Modifier,
    onClearDataClicked: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        OutlinedButton(onClick = onClearDataClicked) {
            Text(stringResource(Res.string.clear_data))
        }
    }
}

@Composable
private fun ClearDataCompactContent(
    onBack: () -> Unit,
    onClearDataClicked: () -> Unit,
) {
    SettingsScaffold(
        title = stringResource(Res.string.account_data_clear_action),
        onBack = onBack,
    ) {
        item {
            ClearDataDetails(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                showAction = true,
                onClearDataClicked = onClearDataClicked,
            )
        }
    }
}

@Composable
private fun ClearDataDetails(
    modifier: Modifier = Modifier,
    showAction: Boolean = false,
    onClearDataClicked: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(Res.string.account_data_clear_action),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(Res.string.clear_data_explanation),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showAction && onClearDataClicked != null) {
            OutlinedButton(onClick = onClearDataClicked) {
                Text(stringResource(Res.string.clear_data))
            }
        }
    }
}
