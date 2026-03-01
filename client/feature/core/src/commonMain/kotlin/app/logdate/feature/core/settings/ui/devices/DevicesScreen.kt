@file:OptIn(ExperimentalMaterial3Api::class)

package app.logdate.feature.core.settings.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.stringResource
import logdate.client.feature.core.generated.resources.*
import logdate.client.feature.core.generated.resources.Res
/**
 * Screen that displays device information and allows management of devices.
 */
@Composable
fun DevicesScreen(
    onBackClick: () -> Unit,
    viewModel: DevicesViewModel = koinInject()
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
    
    if (showRenameDialog && selectedDevice != null) {
        RenameDeviceDialog(
            currentName = selectedDevice!!.name,
            onNameChange = { newDeviceName = it },
            onConfirm = {
                viewModel.renameDevice(newDeviceName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }
    
    if (showDeleteDialog && selectedDevice != null) {
        RemoveDeviceDialog(
            deviceName = selectedDevice!!.name,
            onConfirm = {
                viewModel.removeDevice(selectedDevice!!.id)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
    
    if (showResetDialog) {
        ResetDeviceIdDialog(
            onConfirm = {
                viewModel.resetDeviceId()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.devices)) },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text(stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (uiState.isLoading) {
                    LoadingState()
                } else {
                    DevicesList(
                        devices = uiState.devices,
                        onRenameClick = { device ->
                            selectedDevice = device
                            newDeviceName = device.name
                            showRenameDialog = true
                        },
                        onRemoveClick = { device ->
                            selectedDevice = device
                            showDeleteDialog = true
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.reset_device_id))
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(Res.string.loading_devices))
    }
}

@Composable
private fun DevicesList(
    devices: List<DeviceInfoUiState>,
    onRenameClick: (DeviceInfoUiState) -> Unit,
    onRemoveClick: (DeviceInfoUiState) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        items(devices) { device ->
            DeviceCard(
                device = device,
                onRenameClick = { onRenameClick(device) },
                onRemoveClick = { onRemoveClick(device) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceInfoUiState,
    onRenameClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (device.isCurrentDevice) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.this_device),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Row {
                    IconButton(onClick = onRenameClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(Res.string.rename_device)
                        )
                    }
                    
                    if (!device.isCurrentDevice) {
                        IconButton(onClick = onRemoveClick) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(Res.string.remove_device)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(
                    Res.string.platform_label,
                    device.platformName
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = stringResource(
                    Res.string.last_active_label,
                    device.lastActiveFormatted
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = stringResource(
                    Res.string.app_version_label,
                    device.appVersion
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun RenameDeviceDialog(
    currentName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.rename_device_2)) },
        text = {
            Column {
                Text(stringResource(Res.string.enter_a_new_name_for_this_device))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        onNameChange(it)
                    },
                    label = { Text(stringResource(Res.string.device_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(Res.string.rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

@Composable
private fun RemoveDeviceDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.remove_device_2)) },
        text = {
            Text(
                stringResource(
                    Res.string.remove_device_confirmation,
                    deviceName
                )
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
            ) {
                Text(stringResource(Res.string.remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

@Composable
private fun ResetDeviceIdDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.reset_device_id)) },
        text = {
            Text(stringResource(Res.string.are_you_sure_you_want_to_reset_this_devices_id_this_is_primarily_used_for_privacy_purposes_and_will_generate_a_new_unique_identifier_for_this_device_this_wont_affect_your_data_but_may_require_re_syncing_with_logdate_cloud))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
            ) {
                Text(stringResource(Res.string.reset))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}
