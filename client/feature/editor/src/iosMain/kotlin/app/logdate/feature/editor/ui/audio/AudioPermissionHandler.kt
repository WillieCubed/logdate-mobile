package app.logdate.feature.editor.ui.audio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionStatus
import app.logdate.client.permissions.PermissionType
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.stringResource
import logdate.client.feature.editor.generated.resources.*
import logdate.client.feature.editor.generated.resources.Res
/**
 * iOS implementation of AudioPermissionWrapper.
 * 
 * On iOS, microphone access requires user permission.
 */
@Composable
actual fun AudioPermissionWrapper(
    content: @Composable () -> Unit
) {
    // Get permission manager from Koin
    val permissionManager: PermissionManager = koinInject()
    val coroutineScope = rememberCoroutineScope()
    
    // Track permission state
    var permissionState by remember {
        mutableStateOf(permissionManager.isPermissionGranted(PermissionType.MICROPHONE))
    }
    
    // Check permissions on initial load
    LaunchedEffect(Unit) {
        permissionState = permissionManager.isPermissionGranted(PermissionType.MICROPHONE)
    }
    
    // Request permissions function
    val requestPermissions = {
        coroutineScope.launch {
            permissionManager.requestPermission(PermissionType.MICROPHONE) { result ->
                permissionState = result.status == PermissionStatus.GRANTED
            }
        }
    }
    
    if (permissionState) {
        // Permissions granted, show content
        content()
    } else {
        // Permissions not granted, show request UI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.logdate_needs_access_to_your_microphone_to_record_audio),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { requestPermissions() }
                ) {
                    Text(stringResource(Res.string.grant_permission))
                }
            }
        }
    }
}