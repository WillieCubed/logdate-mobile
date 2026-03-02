@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.settings.ui.dialogs.ResetAppConfirmationDialog
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.action_reset_app
import logdate.client.feature.core.generated.resources.settings_reset_app_description
import logdate.client.feature.core.generated.resources.settings_reset_app_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Danger zone settings screen with destructive actions.
 *
 * This screen automatically adapts to different screen sizes:
 * - Large screens: Acts as a detail pane with minimal header (when in two-pane layout)
 * - Small screens: Standard screen with back navigation
 *
 * @param onBack Callback for when the user presses the back button
 * @param onAppReset Callback to reset the app
 */
@Composable
fun DangerZoneSettingsScreen(
    onBack: () -> Unit,
    onAppReset: () -> Unit,
    viewModel: DangerZoneSettingsViewModel = koinViewModel(),
    isPotentialDetailPane: Boolean? = null,
) {
    val layoutInfo = LocalSettingsLayoutInfo.current
    val resolvedIsDetailPane = isPotentialDetailPane ?: layoutInfo.isDetailPane

    DangerZoneSettingsContent(
        onBack = onBack,
        onAppReset = { viewModel.resetApp { onAppReset() } },
        onClearData = { viewModel.clearLocalData() },
        isPotentialDetailPane = resolvedIsDetailPane,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DangerZoneSettingsContent(
    onBack: () -> Unit,
    onAppReset: () -> Unit,
    onClearData: () -> Unit,
    isPotentialDetailPane: Boolean = false,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier =
            Modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // Only show top bar with back button in single-pane mode
            if (!isPotentialDetailPane) {
                TopAppBar(
                    title = { Text(stringResource(Res.string.danger_zone)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                            titleContentColor = MaterialTheme.colorScheme.error,
                            navigationIconContentColor = MaterialTheme.colorScheme.error,
                        ),
                )
            }
        },
    ) { paddingValues ->
        DefaultSettingsContentContainer {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                // Section title for two-pane mode
                if (isPotentialDetailPane) {
                    item {
                        Text(
                            text = stringResource(Res.string.danger_zone),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        )
                    }
                }
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = stringResource(Res.string.reset_actions),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        MaterialContainer {
                            SurfaceItem {
                                ListItem(
                                    headlineContent = {
                                        Text(stringResource(Res.string.before_you_reset))
                                    },
                                    supportingContent = {
                                        Text(
                                            "Export and verify a backup from Data & Storage. " +
                                                "Without a backup, encrypted local data may be unrecoverable.",
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.WarningAmber,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                )
                            }

                            // Reset App Item
                            SurfaceItem {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = stringResource(Res.string.settings_reset_app_title),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = stringResource(Res.string.settings_reset_app_description),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.RestartAlt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    trailingContent = {
                                        OutlinedButton(
                                            onClick = { showResetDialog = true },
                                            border =
                                                BorderStroke(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.error,
                                                ),
                                            colors =
                                                ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error,
                                                ),
                                        ) {
                                            Text(stringResource(Res.string.action_reset_app))
                                        }
                                    },
                                )
                            }

                            // Clear All Data
                            SurfaceItem {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = stringResource(Res.string.clear_all_data),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = stringResource(Res.string.clear_all_your_data_while_keeping_your_account),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.DeleteForever,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    trailingContent = {
                                        OutlinedButton(
                                            onClick = { showClearDataDialog = true },
                                            border =
                                                BorderStroke(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.error,
                                                ),
                                            colors =
                                                ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error,
                                                ),
                                        ) {
                                            Text(stringResource(Res.string.clear_data))
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Reset App Dialog
        if (showResetDialog) {
            ResetAppConfirmationDialog(
                onDismissRequest = { showResetDialog = false },
                onConfirmation = {
                    onAppReset()
                    showResetDialog = false
                },
            )
        }

        // Clear Data Dialog
        if (showClearDataDialog) {
            AlertDialog(
                onDismissRequest = { showClearDataDialog = false },
                icon = {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                title = {
                    Text(
                        text = stringResource(Res.string.clear_all_data_2),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                text = {
                    Text(
                        text =
                            "⚠️ This action cannot be undone.\n\n" +
                                "This will permanently delete all your journals, entries, photos, " +
                                "and settings.\n\n" +
                                "Your account will remain active.\n\n" +
                                "Are you absolutely sure you want to continue?",
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onClearData()
                            showClearDataDialog = false
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Text(stringResource(Res.string.yes_clear_all_data))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDataDialog = false }) {
                        Text(stringResource(Res.string.cancel))
                    }
                },
            )
        }
    }
}

@Preview
@Composable
private fun DangerZoneSettingsScreenPreview() {
    DangerZoneSettingsContent(
        onBack = {},
        onAppReset = {},
        onClearData = {},
    )
}
