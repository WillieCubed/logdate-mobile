package app.logdate.feature.editor.ui.permissions

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * A reusable component for requesting permissions with explanation.
 * 
 * @param permission The permission being requested
 * @param onRequestPermission Callback when user wants to grant permission
 * @param onDismiss Callback when user dismisses the request
 */
@Composable
fun PermissionRequestContent(
    permission: String,
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Permission Required",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = getPermissionExplanation(permission),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Grant Permission", 
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Not Now",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/**
 * Returns a user-friendly explanation for why the app needs a specific permission.
 */
private fun getPermissionExplanation(permission: String): String {
    return when (permission) {
        Manifest.permission.CAMERA -> 
            "To take photos directly within the app, we need access to your camera. " +
            "This allows you to capture moments without leaving the app."
        
        Manifest.permission.READ_EXTERNAL_STORAGE, 
        "android.permission.READ_MEDIA_IMAGES" -> 
            "To add existing photos from your device, we need access to your media library. " +
            "This allows you to include your photos in journal entries."
        
        Manifest.permission.RECORD_AUDIO ->
            "To record audio notes, we need access to your microphone. " +
            "This allows you to capture voice notes within your journal entries."
            
        else -> 
            "This permission is required for the app to function properly."
    }
}