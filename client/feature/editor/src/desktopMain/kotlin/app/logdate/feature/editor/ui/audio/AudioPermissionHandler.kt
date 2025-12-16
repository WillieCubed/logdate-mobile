package app.logdate.feature.editor.ui.audio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionStatus
import app.logdate.client.permissions.PermissionType
import io.github.aakira.napier.Napier
import org.koin.compose.koinInject

/**
 * Desktop implementation of audio permission wrapper
 * 
 * This implementation verifies microphone access permission using the PermissionManager.
 * On desktop, this typically checks system permissions rather than app permissions.
 * If permission is needed, it shows a dialog to guide the user.
 */
@Composable
actual fun AudioPermissionWrapper(
    content: @Composable () -> Unit
) {
    val permissionManager: PermissionManager = koinInject()
    
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionGranted by remember { 
        mutableStateOf(permissionManager.isPermissionGranted(PermissionType.MICROPHONE))
    }
    
    // Check permission on first composition
    LaunchedEffect(Unit) {
        try {
            // Check via the permission manager
            permissionGranted = permissionManager.isPermissionGranted(PermissionType.MICROPHONE)
            
            // If not granted, show dialog to request
            if (!permissionGranted) {
                showPermissionDialog = true
            }
        } catch (e: Exception) {
            Napier.e("Error checking microphone permission: ${e.message}", e)
            // Default to assuming permission is granted on desktop
            permissionGranted = true
        }
    }
    
    if (permissionGranted) {
        content()
    }
    
    // Show permission dialog if needed
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPermissionDialog = false
                // On desktop, closing this dialog means continuing without permission
                // In a real implementation, we would check the result of a system permission request
                permissionGranted = true
            },
            title = { Text("Microphone Access") },
            text = {
                Column {
                    Text(
                        "LogDate needs access to your microphone to record audio notes. " +
                        "Please grant permission when prompted by your system."
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        "If you don't see a system prompt, you may need to enable microphone " +
                        "access in your system settings.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Request the permission
                        permissionManager.requestPermission(PermissionType.MICROPHONE) { result ->
                            permissionGranted = result.status == PermissionStatus.GRANTED
                        }
                        showPermissionDialog = false
                    }
                ) {
                    Text("Allow Access")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showPermissionDialog = false
                        // For desktop, we might want to let the user try anyway
                        permissionGranted = true
                    }
                ) {
                    Text("Continue Anyway")
                }
            }
        )
    }
}