package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import app.logdate.shared.model.user.AppSecurityLevel
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.settings_biometric_description
import logdate.client.feature.core.generated.resources.settings_biometric_label
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/**
 * Privacy and security settings screen.
 *
 * This screen automatically adapts to different screen sizes:
 * - Large screens: Acts as a detail pane with minimal header (when in two-pane layout)
 * - Small screens: Standard screen with back navigation
 *
 * @param onBack Callback for when the user presses the back button
 * @param onNavigateToLocationSettings Callback for navigating to location settings
 * @param viewModel ViewModel for the settings
 */
@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit,
    onNavigateToLocationSettings: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    // Detect if we're in a large screen layout where this might be a detail pane
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isPotentialDetailPane = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    val uiState by viewModel.uiState.collectAsState()
    val revocationState by viewModel.passkeyRevocationState.collectAsState()
    
    // Convert passkey credentials to PasskeyInfo objects using extension function
    val passkeyInfoList = uiState.currentAccount.toPasskeyInfoList()
    
    val creationState by viewModel.passkeyCreationState.collectAsState()
    
    PrivacySettingsContent(
        onBack = onBack,
        onSetBiometricsEnabled = viewModel::setBiometricEnabled,
        isBiometricsEnabled = uiState.userData.securityLevel == AppSecurityLevel.BIOMETRIC,
        passkeys = passkeyInfoList,
        onCreatePasskey = { viewModel.createPasskey() },
        onRevokePasskey = { passkey -> viewModel.revokePasskey(passkey.id) },
        onNavigateToLocationSettings = onNavigateToLocationSettings,
        revocationState = revocationState,
        creationState = creationState,
        isPotentialDetailPane = isPotentialDetailPane
    )
}

/**
 * Composable for confirming passkey deletion.
 *
 * @param passkey The passkey to delete, or null if no confirmation is needed
 * @param onConfirm Callback for when the user confirms deletion
 * @param onDismiss Callback for when the user dismisses the dialog
 */
@Composable
private fun PasskeyDeletionConfirmationDialog(
    passkey: PasskeyInfo?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (passkey != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Remove passkey?") },
            text = { Text("This will remove your passkey from '${passkey.device}'. You'll no longer be able to use it to sign in.") },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Composable for showing a loading dialog during passkey operations.
 *
 * @param operation The type of operation ("Removing" or "Creating")
 * @param message The message to display
 * @param isLoading Whether the loading dialog should be shown
 */
@Composable
private fun PasskeyOperationLoadingDialog(
    operation: String = "Removing",
    message: String = "Please wait while we remove your passkey",
    isLoading: Boolean
) {
    if (isLoading) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissal during operation */ },
            title = { Text("$operation passkey...") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = Spacing.md)
                    )
                }
            },
            confirmButton = { }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacySettingsContent(
    onBack: () -> Unit,
    onSetBiometricsEnabled: (enabled: Boolean) -> Unit,
    isBiometricsEnabled: Boolean,
    passkeys: List<PasskeyInfo> = emptyList(),
    onCreatePasskey: () -> Unit = {},
    onRevokePasskey: (PasskeyInfo) -> Unit = {},
    onNavigateToLocationSettings: () -> Unit = {},
    revocationState: PasskeyRevocationState = PasskeyRevocationState.Idle,
    creationState: PasskeyCreationState = PasskeyCreationState.Idle,
    isPotentialDetailPane: Boolean = false
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    var passkeyToDelete by remember { mutableStateOf<PasskeyInfo?>(null) }
    
    // Show feedback for passkey operations
    LaunchedEffect(revocationState) {
        when (revocationState) {
            is PasskeyRevocationState.Success -> {
                snackbarHostState.showSnackbar("Passkey successfully removed")
            }
            is PasskeyRevocationState.Error -> {
                snackbarHostState.showSnackbar("Failed to remove passkey: ${revocationState.message}")
            }
            else -> { /* No action needed */ }
        }
    }
    
    LaunchedEffect(creationState) {
        when (creationState) {
            is PasskeyCreationState.Success -> {
                snackbarHostState.showSnackbar("Passkey successfully created")
            }
            is PasskeyCreationState.Error -> {
                snackbarHostState.showSnackbar("Failed to create passkey: ${creationState.message}")
            }
            else -> { /* No action needed */ }
        }
    }
    
    // Confirmation dialog for passkey deletion
    PasskeyDeletionConfirmationDialog(
        passkey = passkeyToDelete,
        onConfirm = {
            passkeyToDelete?.let { onRevokePasskey(it) }
            passkeyToDelete = null
        },
        onDismiss = { passkeyToDelete = null }
    )
    
    // Loading dialogs for passkey operations
    PasskeyOperationLoadingDialog(
        operation = "Removing",
        message = "Please wait while we remove your passkey",
        isLoading = revocationState == PasskeyRevocationState.Revoking
    )
    
    PasskeyOperationLoadingDialog(
        operation = "Creating",
        message = "Please wait while we create your passkey",
        isLoading = creationState == PasskeyCreationState.Creating
    )
    
    Scaffold(
        modifier = Modifier
            .applyScreenStyles()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // Only show top bar with back button in single-pane mode
            if (!isPotentialDetailPane) {
                TopAppBar(
                    title = { Text("Privacy & Security") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                            text = "Privacy & Security",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        )
                    }
                }
                
                // App Security section
            item {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "App Security",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    MaterialContainer {
                        SurfaceItem {
                            ListItem(
                                headlineContent = { Text(stringResource(Res.string.settings_biometric_label)) },
                                supportingContent = { Text(stringResource(Res.string.settings_biometric_description)) },
                                trailingContent = {
                                    Switch(
                                        checked = isBiometricsEnabled,
                                        onCheckedChange = onSetBiometricsEnabled
                                    )
                                }
                            )
                        }
                        
                        SurfaceItem {
                            ListItem(
                                headlineContent = { Text("App Lock") },
                                supportingContent = { Text("Lock the app when not in use") },
                                trailingContent = {
                                    Switch(
                                        checked = false,
                                        onCheckedChange = { /* TODO: Implement */ }
                                    )
                                }
                            )
                        }
                    }
                }
            }
            
            // Location Settings Section
            item {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Location Privacy",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    MaterialContainer {
                        SurfaceItem {
                            ListItem(
                                headlineContent = { Text("Location Settings") },
                                supportingContent = { Text("Manage location tracking and privacy preferences") },
                                leadingContent = { 
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "Navigate to Location Settings"
                                    )
                                },
                                modifier = Modifier.clickable(onClick = onNavigateToLocationSettings)
                            )
                        }
                    }
                }
            }
            
            // Passkeys management section
            item {
                PasskeysInfoSection(
                    passkeys = passkeys,
                    onCreatePasskey = onCreatePasskey,
                    onRevokePasskey = { passkey -> passkeyToDelete = passkey },
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            }
            }
        }
    }
}

@Preview
@Composable
private fun PrivacySettingsScreenPreview() {
    PrivacySettingsContent(
        onBack = {},
        onSetBiometricsEnabled = {},
        isBiometricsEnabled = true,
        passkeys = listOf(
            PasskeyInfo(
                id = "1",
                name = "Passkey #1",
                device = "Pixel 7",
                createdAt = "2024-03-13"
            )
        ),
        revocationState = PasskeyRevocationState.Idle,
        creationState = PasskeyCreationState.Idle
    )
}

@Preview
@Composable
private fun PrivacySettingsScreenEmptyPasskeysPreview() {
    PrivacySettingsContent(
        onBack = {},
        onSetBiometricsEnabled = {},
        isBiometricsEnabled = false,
        passkeys = emptyList()
    )
}

@Preview
@Composable
private fun PrivacySettingsScreenLoadingRevocationPreview() {
    PrivacySettingsContent(
        onBack = {},
        onSetBiometricsEnabled = {},
        isBiometricsEnabled = true,
        passkeys = listOf(
            PasskeyInfo(
                id = "1",
                name = "Passkey #1",
                device = "Pixel 7",
                createdAt = "2024-03-13"
            )
        ),
        revocationState = PasskeyRevocationState.Revoking
    )
}

@Preview
@Composable
private fun PrivacySettingsScreenLoadingCreationPreview() {
    PrivacySettingsContent(
        onBack = {},
        onSetBiometricsEnabled = {},
        isBiometricsEnabled = true,
        passkeys = listOf(
            PasskeyInfo(
                id = "1",
                name = "Passkey #1",
                device = "Pixel 7",
                createdAt = "2024-03-13"
            )
        ),
        creationState = PasskeyCreationState.Creating
    )
}