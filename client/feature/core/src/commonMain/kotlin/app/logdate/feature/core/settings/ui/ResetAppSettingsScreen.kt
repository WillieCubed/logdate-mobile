@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.settings.ui.dialogs.ResetAppConfirmationDialog
import app.logdate.ui.adaptive.FoldableBookLayout
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

    FoldableBookLayout(
        modifier = Modifier.fillMaxSize(),
        minPaneWidth = 320.dp,
        startPane = {
            ResetAppIntroPane(
                modifier = Modifier.fillMaxSize(),
            )
        },
        endPane = {
            ResetAppActionPane(
                modifier = Modifier.fillMaxSize(),
                onResetAppClicked = { showDialog = true },
            )
        },
        standardContent = {
            SettingsScaffold(
                title = stringResource(Res.string.settings_reset_app_title),
                onBack = onBack,
            ) {
                item {
                    ResetAppCompactContent(
                        onResetAppClicked = { showDialog = true },
                    )
                }
            }
        },
    )

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

@Composable
private fun ResetAppIntroPane(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(Res.string.settings_reset_app_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(Res.string.reset_app_explanation),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResetAppActionPane(
    modifier: Modifier = Modifier,
    onResetAppClicked: () -> Unit,
) {
    Column(
        modifier =
            modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        OutlinedButton(onClick = onResetAppClicked) {
            Text(stringResource(Res.string.action_reset_app))
        }
    }
}

@Composable
private fun ResetAppCompactContent(onResetAppClicked: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(Res.string.reset_app_explanation),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onResetAppClicked) {
            Text(stringResource(Res.string.action_reset_app))
        }
    }
}
