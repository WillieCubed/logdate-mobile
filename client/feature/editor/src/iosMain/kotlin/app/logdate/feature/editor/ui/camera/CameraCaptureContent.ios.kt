@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView

/**
 * iOS camera UI for the entry editor. Hosts the live `AVCaptureVideoPreviewLayer` from
 * [IosCameraCaptureManager] inside a `UIKitView` and exposes capture / switch / mode toggle / close
 * affordances matching the Android version's primary actions.
 */
@Composable
actual fun CameraCaptureContent(
    onMediaCaptured: (uri: String, mediaType: CapturedMediaType, durationMs: Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val manager = koinInject<CameraCaptureManager>() as? IosCameraCaptureManager
    if (manager == null) {
        UnavailableCameraCard(onClose = onClose, modifier = modifier)
        return
    }
    val state by manager.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.cameraFacing) {
        manager.startPreview(state.cameraFacing)
    }
    DisposableEffect(manager) {
        onDispose {
            scope.launch { manager.stopPreview() }
        }
    }

    Box(modifier = modifier.fillMaxWidth().aspectRatio(state.aspectRatio.ratio).background(Color.Black)) {
        UIKitView(
            factory = {
                val container = UIView()
                container.layer.addSublayer(manager.previewLayer)
                container
            },
            update = { container ->
                container.bounds.useContents {
                    manager.previewLayer.frame = CGRectMake(0.0, 0.0, size.width, size.height)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (state.error != null) {
            ErrorOverlay(error = state.error!!)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            Spacer(Modifier.height(0.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = { scope.launch { manager.switchCamera() } },
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "Switch", tint = Color.White)
                    }
                    val isVideo = state.captureMode == CaptureMode.VIDEO
                    val isRecording = state.isRecording
                    Button(
                        onClick = {
                            scope.launch {
                                if (isVideo) {
                                    if (isRecording) {
                                        val uri = manager.stopVideoRecording()
                                        if (uri != null) {
                                            onMediaCaptured(uri, CapturedMediaType.VIDEO, 0L)
                                        }
                                    } else {
                                        manager.startVideoRecording()
                                    }
                                } else {
                                    val uri = manager.capturePhoto()
                                    if (uri != null) {
                                        onMediaCaptured(uri, CapturedMediaType.PHOTO, 0L)
                                    }
                                }
                            }
                        },
                        shape = CircleShape,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) Color.Red else Color.White,
                            ),
                        modifier = Modifier.size(72.dp).align(Alignment.Center),
                    ) {
                        val (icon, label) =
                            when {
                                isVideo && isRecording -> Icons.Default.Stop to "Stop"
                                isVideo -> Icons.Default.FiberManualRecord to "Record"
                                else -> Icons.Default.CameraAlt to "Capture"
                            }
                        Icon(
                            icon,
                            contentDescription = label,
                            tint = if (isRecording) Color.White else Color.Black,
                        )
                    }
                    IconButton(
                        onClick = {
                            manager.setCaptureMode(if (isVideo) CaptureMode.PHOTO else CaptureMode.VIDEO)
                        },
                        modifier = Modifier.align(Alignment.CenterEnd),
                        enabled = !isRecording,
                    ) {
                        Icon(
                            imageVector = if (isVideo) Icons.Default.CameraAlt else Icons.Default.Videocam,
                            contentDescription = if (isVideo) "Switch to photo" else "Switch to video",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnavailableCameraCard(
    onClose: () -> Unit,
    modifier: Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().height(200.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Camera unavailable on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.TextButton(onClick = onClose) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun ErrorOverlay(error: CameraCaptureError) {
    val message =
        when (error) {
            CameraCaptureError.PermissionDenied -> "Camera permission required. Open Settings to enable."
            CameraCaptureError.CameraNotAvailable -> "No camera available on this device."
            CameraCaptureError.CaptureFailed -> "Couldn't capture photo. Try again."
            CameraCaptureError.RecordingFailed -> "Couldn't record video. Try again."
            is CameraCaptureError.Unknown -> "Camera error: ${error.message}"
        }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(32.dp),
        )
    }
}
