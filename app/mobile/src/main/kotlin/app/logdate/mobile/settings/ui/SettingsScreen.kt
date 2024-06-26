package app.logdate.mobile.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.core.datastore.model.AppSecurityLevel
import app.logdate.core.datastore.model.UserData
import app.logdate.mobile.R
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.Clock

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onReset: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.userDataState.collectAsState()
    when (state) {
        is SettingsUiState.Loading -> {
            // TODO: Add loading indicator

        }

        is SettingsUiState.Loaded -> {
            SettingsContent(
                onBack = onBack,
                onReset = onReset,
                appSettings = (state as SettingsUiState.Loaded).userData,
                onSetBiometricsEnabled = viewModel::setBiometricEnabled,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    onBack: () -> Unit,
    onReset: () -> Unit,
    onSetBiometricsEnabled: (enabled: Boolean) -> Unit,
    appSettings: UserData,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
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
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_biometric_label)) },
                    supportingContent = { Text(stringResource(R.string.settings_biometric_description)) },
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        stringResource(R.string.settings_reset_app_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.settings_reset_app_description),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = {
                        showResetDialog = true
                    }) {
                        Text(stringResource(R.string.action_reset_app_long))
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
            Text(stringResource(R.string.dialog_reset_app_title))
        },
        text = {
            Text(stringResource(R.string.dialog_reset_app_description))
        },
        confirmButton = {
            Button(onClick = onConfirmation) {
                Text(stringResource(R.string.action_reset_app))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
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
            isOnboarded = true,
            onboardedDate = Clock.System.now(),
            securityLevel = AppSecurityLevel.BIOMETRIC,
            favoriteNotes = emptyList(),
        ),
        onSetBiometricsEnabled = {},
    )
}