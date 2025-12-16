package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import app.logdate.feature.core.settings.ui.dialogs.ResetAppConfirmationDialog
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.action_reset_app
import logdate.client.feature.core.generated.resources.settings_reset_app_description
import logdate.client.feature.core.generated.resources.settings_reset_app_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

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
) {
    // Detect if we're in a large screen layout where this might be a detail pane
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isPotentialDetailPane = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    
    DangerZoneSettingsContent(
        onBack = onBack,
        onAppReset = onAppReset,
        isPotentialDetailPane = isPotentialDetailPane
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DangerZoneSettingsContent(
    onBack: () -> Unit,
    onAppReset: () -> Unit,
    isPotentialDetailPane: Boolean = false
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showResetDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .applyScreenStyles()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // Only show top bar with back button in single-pane mode
            if (!isPotentialDetailPane) {
                TopAppBar(
                    title = { Text("Danger Zone") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                        titleContentColor = MaterialTheme.colorScheme.error,
                        navigationIconContentColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        },
    ) { paddingValues ->
        DefaultSettingsContentContainer {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // Section title for two-pane mode
                if (isPotentialDetailPane) {
                    item {
                        Text(
                            text = "Danger Zone",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        )
                    }
                }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Reset Actions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    MaterialContainer {
                        // Reset App Item
                        SurfaceItem {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = stringResource(Res.string.settings_reset_app_title),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = stringResource(Res.string.settings_reset_app_description),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.RestartAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                trailingContent = {
                                    OutlinedButton(
                                        onClick = { showResetDialog = true },
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.error
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text(stringResource(Res.string.action_reset_app))
                                    }
                                }
                            )
                        }

                        // Clear All Data
                        SurfaceItem {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = "Clear All Data",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "Clear all your data while keeping your account",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                trailingContent = {
                                    OutlinedButton(
                                        onClick = { showClearDataDialog = true },
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.error
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Clear Data")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Account Actions
            item {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Account Actions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    MaterialContainer {
                        // Delete Account
                        SurfaceItem {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = "Delete Account",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "Permanently delete your account and all associated data",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                trailingContent = {
                                    Button(
                                        onClick = { showDeleteAccountDialog = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Delete")
                                    }
                                }
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
                }
            )
        }

        // Delete Account Dialog
        if (showDeleteAccountDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAccountDialog = false },
                icon = {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                        text = "Delete Account?",
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text(
                        text = "⚠️ This action cannot be undone.\n\n" +
                                "This will permanently delete your account and all your data " +
                                "from our servers.\n\n" +
                                "Are you absolutely sure you want to continue?"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // TODO: Implement account deletion
                            showDeleteAccountDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Yes, Delete My Account")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAccountDialog = false }) {
                        Text("Cancel")
                    }
                }
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
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                        text = "Clear All Data?",
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text(
                        text = "⚠️ This action cannot be undone.\n\n" +
                                "This will permanently delete all your journals, entries, photos, " +
                                "and settings.\n\n" +
                                "Your account will remain active.\n\n" +
                                "Are you absolutely sure you want to continue?"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // TODO: Implement data clearing
                            showClearDataDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Yes, Clear All Data")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDataDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Preview
@Composable
private fun DangerZoneSettingsScreenPreview() {
    DangerZoneSettingsContent(
        onBack = {},
        onAppReset = {}
    )
}