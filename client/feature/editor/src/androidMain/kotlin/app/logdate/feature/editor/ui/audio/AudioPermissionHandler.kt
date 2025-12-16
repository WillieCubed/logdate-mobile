package app.logdate.feature.editor.ui.audio

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionStatus
import app.logdate.client.permissions.PermissionType
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Wrapper component that handles audio recording permissions for Android.
 * If permissions are not granted, displays a request UI.
 * If permissions are granted, displays the audio editor content.
 */
@Composable
actual fun AudioPermissionWrapper(
    content: @Composable () -> Unit
) {
    // Get permission manager from Koin
    val permissionManager: PermissionManager = koinInject()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Track permission state
    var permissionState by remember {
        mutableStateOf(
            permissionManager.isPermissionGranted(PermissionType.MICROPHONE)
        )
    }
    
    var showRationale by remember { mutableStateOf(false) }
    
    // Check permissions on initial load
    LaunchedEffect(Unit) {
        permissionState = permissionManager.isPermissionGranted(PermissionType.MICROPHONE)
        
        showRationale = !permissionState && 
            permissionManager.shouldShowRationale(PermissionType.MICROPHONE)
        
        Napier.d("Initial microphone permission state: $permissionState")
    }
    
    // Create permission launcher for microphone permission
    val requestAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        Napier.d("Microphone permission result: $isGranted")
        permissionState = isGranted
        if (!isGranted) {
            showRationale = permissionManager.shouldShowRationale(PermissionType.MICROPHONE)
        }
    }
    
    // Function to request microphone permission using the Activity launcher
    val requestPermissions = {
        Napier.d("Requesting microphone permission")
        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                    text = if (showRationale) 
                        "Microphone permission is required to record audio notes. Please enable it in app settings." 
                    else 
                        "LogDate needs access to your microphone to record audio notes. This permission is only used when you actively choose to record audio.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (showRationale) {
                    Button(
                        onClick = { 
                            permissionManager.openAppSettings()
                        }
                    ) {
                        Text("Open App Settings")
                    }
                } else {
                    Button(
                        onClick = { requestPermissions() }
                    ) {
                        Text("Allow Microphone Access")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "You can record audio notes after granting permission",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}