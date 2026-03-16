package app.logdate.wear.presentation.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close

@Composable
fun WearRemoteCameraScreen(
    viewModel: WearRemoteCameraViewModel,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.navigateBack) {
        onNavigateBack()
        return
    }

    when (uiState.phase) {
        RemoteCameraPhase.IDLE -> IdleContent(onRequest = viewModel::requestCamera)
        RemoteCameraPhase.REQUESTING -> RequestingContent()
        RemoteCameraPhase.READY -> ReadyContent(
            onCapture = viewModel::capture,
            onCancel = viewModel::dismiss,
        )
        RemoteCameraPhase.CAPTURING -> CapturingContent()
        RemoteCameraPhase.PREVIEW -> PreviewContent(
            onSave = viewModel::dismiss,
            onMore = viewModel::captureMore,
        )
        RemoteCameraPhase.ERROR -> ErrorContent(
            message = uiState.errorMessage,
            onDismiss = viewModel::dismiss,
        )
    }
}

@Composable
private fun IdleContent(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Button(onClick = onRequest) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Open camera",
                modifier = Modifier.size(24.dp),
            )
            Text("Camera")
        }
    }
}

@Composable
private fun RequestingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Opening camera\non your phone...",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReadyContent(onCapture: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Camera ready",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(12.dp))
        IconButton(
            onClick = onCapture,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Take photo",
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

@Composable
private fun CapturingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Capturing...",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PreviewContent(onSave: () -> Unit, onMore: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Photo captured",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave) {
                Text("Done")
            }
            Button(onClick = onMore) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Take another",
                    modifier = Modifier.size(16.dp),
                )
                Text("More")
            }
        }
    }
}

@Composable
private fun ErrorContent(message: String?, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message ?: "Something went wrong",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onDismiss) {
            Text("Back")
        }
    }
}
