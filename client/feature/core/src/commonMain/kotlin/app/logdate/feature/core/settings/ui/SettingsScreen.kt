package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.logdate.shared.model.user.AppSecurityLevel
import app.logdate.shared.model.user.UserData
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.Clock
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.action_reset_app
import logdate.client.feature.core.generated.resources.action_reset_app_long
import logdate.client.feature.core.generated.resources.dialog_reset_app_description
import logdate.client.feature.core.generated.resources.dialog_reset_app_title
import logdate.client.feature.core.generated.resources.screen_title_settings
import logdate.client.feature.core.generated.resources.settings_biometric_description
import logdate.client.feature.core.generated.resources.settings_biometric_label
import logdate.client.feature.core.generated.resources.settings_export_entries_description
import logdate.client.feature.core.generated.resources.settings_export_entries_label
import logdate.client.feature.core.generated.resources.settings_reset_app_description
import logdate.client.feature.core.generated.resources.settings_reset_app_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAppReset: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.userDataState.collectAsState()
    when (state) {
        is SettingsUiState.Loading -> {
            // TODO: Add loading indicator
        }

        is SettingsUiState.Loaded -> {
            SettingsContent(
                onBack = onBack,
                onReset = onAppReset,
                appSettings = (state as SettingsUiState.Loaded).userData,
                onSetBiometricsEnabled = viewModel::setBiometricEnabled,
                onExportContent = viewModel::exportContent,
            )
        }
    }
}

@Composable
private fun UpdateBirthdayDialog(
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
//        DatePickerDialog(
//            onDateSelected = { date ->
//                selectedDate = date
//                showDialog = false
//            },
//            onDismissRequest = {
//                showDialog = false
//            }
//        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    onBack: () -> Unit,
    onReset: () -> Unit,
    onSetBiometricsEnabled: (enabled: Boolean) -> Unit,
    onExportContent: () -> Unit,
    appSettings: UserData,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    val birthdayFieldState = rememberDatePickerState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = it,
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        "Your info",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.settings_biometric_label)) },
                    supportingContent = { Text(stringResource(Res.string.settings_biometric_description)) },
                    leadingContent = {
                        Switch(
                            checked = appSettings.securityLevel == AppSecurityLevel.BIOMETRIC,
                            onCheckedChange = { enabled ->
                                onSetBiometricsEnabled(enabled)
                            },
                        )
                    },
                )
            }
            item {
                ListItem(
                    modifier = Modifier.padding(top = Spacing.sm),
                    headlineContent = { Text(stringResource(Res.string.settings_export_entries_label)) },
                    supportingContent = { Text(stringResource(Res.string.settings_export_entries_description)) },
//                    onClick = onExportContent,
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(Res.string.settings_reset_app_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(Res.string.settings_reset_app_description),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = {
                        showResetDialog = true
                    }) {
                        Text(stringResource(Res.string.action_reset_app_long))
                    }
                }
            }
        }
        if (showResetDialog) {
            ConfirmResetDialog(
                onDismissRequest = { showResetDialog = false },
                onConfirmation = {
                    onReset()
                    showResetDialog = false
                },
            )
        }
    }
}

@Composable
internal fun ConfirmResetDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(Icons.Default.WarningAmber, contentDescription = null)
        },
        title = {
            Text(stringResource(Res.string.dialog_reset_app_title))
        },
        text = {
            Text(stringResource(Res.string.dialog_reset_app_description))
        },
        confirmButton = {
            Button(onClick = onConfirmation) {
                Text(stringResource(Res.string.action_reset_app))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel") // TODO: Extract to compose resource
            }
        },
    )
}

@Preview
@Composable
private fun PreviewSettingsScreen() {
    SettingsContent(
        onBack = {},
        onReset = {},
        appSettings = UserData(
            birthday = Clock.System.now(),
            isOnboarded = true,
            onboardedDate = Clock.System.now(),
            securityLevel = AppSecurityLevel.BIOMETRIC,
            favoriteNotes = emptyList(),
        ),
        onSetBiometricsEnabled = {},
        onExportContent = {},
    )
}