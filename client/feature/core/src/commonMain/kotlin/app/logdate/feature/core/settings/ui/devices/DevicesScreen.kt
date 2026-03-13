@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.app_version_label
import logdate.client.feature.core.generated.resources.back
import logdate.client.feature.core.generated.resources.cancel
import logdate.client.feature.core.generated.resources.device_name
import logdate.client.feature.core.generated.resources.devices
import logdate.client.feature.core.generated.resources.enter_a_new_name_for_this_device
import logdate.client.feature.core.generated.resources.last_active_label
import logdate.client.feature.core.generated.resources.loading_devices
import logdate.client.feature.core.generated.resources.platform_label
import logdate.client.feature.core.generated.resources.remove
import logdate.client.feature.core.generated.resources.remove_device
import logdate.client.feature.core.generated.resources.remove_device_2
import logdate.client.feature.core.generated.resources.remove_device_confirmation
import logdate.client.feature.core.generated.resources.rename
import logdate.client.feature.core.generated.resources.rename_device
import logdate.client.feature.core.generated.resources.rename_device_2
import logdate.client.feature.core.generated.resources.reset
import logdate.client.feature.core.generated.resources.reset_device_id
import logdate.client.feature.core.generated.resources.reset_device_id_confirmation
import logdate.client.feature.core.generated.resources.this_device
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Screen that displays device information and allows management of devices.
 */
@Composable
fun DevicesScreen(
    onBackClick: () -> Unit,
    viewModel: DevicesViewModel = koinInject(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDevices()
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<DeviceInfoUiState?>(null) }
    var newDeviceName by remember { mutableStateOf("") }

    DevicesScreenContent(
        onBackClick = onBackClick,
        uiState = uiState,
        showRenameDialog = showRenameDialog,
        showDeleteDialog = showDeleteDialog,
        showResetDialog = showResetDialog,
        selectedDevice = selectedDevice,
        newDeviceName = newDeviceName,
        onNewDeviceNameChange = { newDeviceName = it },
        onRenameClick = { device ->
            selectedDevice = device
            newDeviceName = device.name
            showRenameDialog = true
        },
        onRemoveClick = { device ->
            selectedDevice = device
            showDeleteDialog = true
        },
        onRenameConfirm = {
            viewModel.renameDevice(newDeviceName)
            showRenameDialog = false
        },
        onRemoveConfirm = {
            selectedDevice?.let { viewModel.removeDevice(it.id) }
            showDeleteDialog = false
        },
        onResetDeviceId = {
            viewModel.resetDeviceId()
            showResetDialog = false
        },
        onShowResetDialog = { showResetDialog = true },
        onDismissRenameDialog = { showRenameDialog = false },
        onDismissDeleteDialog = { showDeleteDialog = false },
        onDismissResetDialog = { showResetDialog = false },
    )
}

@Composable
fun DevicesScreenContent(
    onBackClick: () -> Unit,
    uiState: DevicesUiState,
    showRenameDialog: Boolean = false,
    showDeleteDialog: Boolean = false,
    showResetDialog: Boolean = false,
    selectedDevice: DeviceInfoUiState? = null,
    newDeviceName: String = selectedDevice?.name.orEmpty(),
    onNewDeviceNameChange: (String) -> Unit = {},
    onRenameClick: (DeviceInfoUiState) -> Unit = {},
    onRemoveClick: (DeviceInfoUiState) -> Unit = {},
    onRenameConfirm: () -> Unit = {},
    onRemoveConfirm: () -> Unit = {},
    onResetDeviceId: () -> Unit = {},
    onShowResetDialog: () -> Unit = {},
    onDismissRenameDialog: () -> Unit = {},
    onDismissDeleteDialog: () -> Unit = {},
    onDismissResetDialog: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    if (showRenameDialog && selectedDevice != null) {
        RenameDeviceDialog(
            currentName = newDeviceName.ifBlank { selectedDevice.name },
            onNameChange = onNewDeviceNameChange,
            onConfirm = onRenameConfirm,
            onDismiss = onDismissRenameDialog,
        )
    }

    if (showDeleteDialog && selectedDevice != null) {
        RemoveDeviceDialog(
            deviceName = selectedDevice.name,
            onConfirm = onRemoveConfirm,
            onDismiss = onDismissDeleteDialog,
        )
    }

    if (showResetDialog) {
        ResetDeviceIdDialog(
            onConfirm = onResetDeviceId,
            onDismiss = onDismissResetDialog,
        )
    }

    Scaffold(
        modifier =
            modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(Res.string.devices)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        DefaultSettingsContentContainer {
            if (uiState.isLoading) {
                LoadingState(modifier = Modifier.padding(paddingValues))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = paddingValues,
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    // Device list
                    items(uiState.devices) { device ->
                        DeviceItem(
                            device = device,
                            onRenameClick = { onRenameClick(device) },
                            onRemoveClick = { onRemoveClick(device) },
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        )
                    }

                    // Reset Device ID action
                    item {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Button(onClick = onShowResetDialog) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(stringResource(Res.string.reset_device_id))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(stringResource(Res.string.loading_devices))
    }
}

@Composable
private fun DeviceItem(
    device: DeviceInfoUiState,
    onRenameClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MaterialContainer(modifier = modifier) {
        SurfaceItem {
            ListItem(
                headlineContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(device.name)
                        if (device.isCurrentDevice) {
                            Text(
                                text = stringResource(Res.string.this_device),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(stringResource(Res.string.platform_label, device.platformName))
                        Text(stringResource(Res.string.last_active_label, device.lastActiveFormatted))
                        Text(stringResource(Res.string.app_version_label, device.appVersion))
                    }
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = onRenameClick) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(Res.string.rename_device),
                            )
                        }
                        if (!device.isCurrentDevice) {
                            IconButton(onClick = onRemoveClick) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(Res.string.remove_device),
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun RenameDeviceDialog(
    currentName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.rename_device_2)) },
        text = {
            Column {
                Text(stringResource(Res.string.enter_a_new_name_for_this_device))
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onNameChange(it)
                    },
                    label = { Text(stringResource(Res.string.device_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(Res.string.rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
fun RemoveDeviceDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.remove_device_2)) },
        text = {
            Text(
                stringResource(
                    Res.string.remove_device_confirmation,
                    deviceName,
                ),
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(Res.string.remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
fun ResetDeviceIdDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.reset_device_id)) },
        text = {
            Text(
                stringResource(Res.string.reset_device_id_confirmation),
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(Res.string.reset))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
