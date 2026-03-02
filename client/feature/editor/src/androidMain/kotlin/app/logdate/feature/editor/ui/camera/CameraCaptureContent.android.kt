package app.logdate.feature.editor.ui.camera

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.logdate.feature.editor.ui.photovideo.CameraType
import app.logdate.feature.editor.ui.photovideo.LiveCameraPreview
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import io.github.aakira.napier.Napier
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.close_camera
import logdate.client.feature.editor.generated.resources.photo
import logdate.client.feature.editor.generated.resources.switch_camera
import logdate.client.feature.editor.generated.resources.tap_to_enable_camera
import logdate.client.feature.editor.generated.resources.video
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Android implementation of the camera capture content.
 * Shows an inline camera with controls for capturing photos/videos.
 */
@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun CameraCaptureContent(
    onMediaCaptured: (uri: String, mediaType: CapturedMediaType, durationMs: Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val viewModel: CameraViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    val cameraPermissions =
        rememberMultiplePermissionsState(
            permissions =
                listOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                ),
        )

    val hasPermissions by remember {
        derivedStateOf { cameraPermissions.allPermissionsGranted }
    }

    LaunchedEffect(cameraPermissions.permissions) {
        Napier.d(
            "Camera permissions state: ${cameraPermissions.permissions.map {
                "${it.permission}: ${it.status}"
            }}",
        )
    }

    LaunchedEffect(uiState.capturedMediaUri) {
        uiState.capturedMediaUri?.let { uri ->
            val mediaType = uiState.capturedMediaType ?: CapturedMediaType.PHOTO
            val duration = if (mediaType == CapturedMediaType.VIDEO) uiState.recordingDurationMs else 0L
            Napier.d("CameraCaptureContent - Captured media: $uri, type: $mediaType, duration: $duration")
            onMediaCaptured(uri, mediaType, duration)
            viewModel.clearCapturedMedia()
        }
    }

    if (!hasPermissions) {
        CameraPermissionRequest(
            onRequestPermission = { cameraPermissions.launchMultiplePermissionRequest() },
            modifier = modifier,
        )
    } else {
        InlineCameraCapture(
            viewModel = viewModel,
            uiState = uiState,
            onClose = onClose,
            modifier = modifier,
        )
    }
}

/**
 * Displays a request UI when camera permissions are not granted.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun CameraPermissionRequest(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onRequestPermission,
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 200.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.tap_to_enable_camera),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * First-class inline camera capture interface with live preview and controls.
 * Works like Instagram stories - capture directly without opening fullscreen.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun InlineCameraCapture(
    viewModel: CameraViewModel,
    uiState: CameraUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        val manager = viewModel.getCaptureManager()
        if (manager is AndroidCameraCaptureManager) {
            manager.setLifecycleOwner(lifecycleOwner)
        }
        viewModel.startPreview()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPreview()
        }
    }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxSize(),
        colors =
            CardDefaults.cardColors(
                containerColor = Color.Black,
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val cameraType =
                when (uiState.cameraFacing) {
                    CameraFacing.FRONT -> CameraType.FRONT
                    CameraFacing.BACK -> CameraType.BACK
                }

            LiveCameraPreview(
                canUseCamera = true,
                cameraType = cameraType,
                modifier = Modifier.fillMaxSize(),
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier =
                        Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.close_camera),
                        tint = Color.White,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (uiState.isRecording) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Red.copy(alpha = 0.8f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FiberManualRecord,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = uiState.formattedDuration,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(
                        onClick = { viewModel.switchCamera() },
                        enabled = !uiState.isRecording,
                        modifier =
                            Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = stringResource(Res.string.switch_camera),
                            tint = if (uiState.isRecording) Color.Gray else Color.White,
                        )
                    }
                }
            }

            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = !uiState.isRecording,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    PhotoVideoToggle(
                        currentMode = uiState.captureMode,
                        onModeChanged = { viewModel.setCaptureMode(it) },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                ShutterButton(
                    isRecording = uiState.isRecording,
                    captureMode = uiState.captureMode,
                    isCapturing = uiState.isCapturing,
                    onClick = { viewModel.capture() },
                )
            }

            uiState.error?.let { error ->
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * Toggle between photo and video capture modes.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun PhotoVideoToggle(
    currentMode: CaptureMode,
    onModeChanged: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilterChip(
            selected = currentMode == CaptureMode.PHOTO,
            onClick = { onModeChanged(CaptureMode.PHOTO) },
            label = { Text(stringResource(Res.string.photo)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color.White,
                    selectedLabelColor = Color.Black,
                    selectedLeadingIconColor = Color.Black,
                    containerColor = Color.Transparent,
                    labelColor = Color.White,
                    iconColor = Color.White,
                ),
        )

        FilterChip(
            selected = currentMode == CaptureMode.VIDEO,
            onClick = { onModeChanged(CaptureMode.VIDEO) },
            label = { Text(stringResource(Res.string.video)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color.White,
                    selectedLabelColor = Color.Black,
                    selectedLeadingIconColor = Color.Black,
                    containerColor = Color.Transparent,
                    labelColor = Color.White,
                    iconColor = Color.White,
                ),
        )
    }
}

/**
 * Shutter button for capturing photos or starting/stopping video recording.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun ShutterButton(
    isRecording: Boolean,
    captureMode: CaptureMode,
    isCapturing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonSize = 72.dp
    val innerSize = if (isRecording) 24.dp else 56.dp

    Surface(
        modifier =
            modifier
                .size(buttonSize)
                .clickable(enabled = !isCapturing) { onClick() },
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.3f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(innerSize),
                shape = if (isRecording) RoundedCornerShape(4.dp) else CircleShape,
                color = if (captureMode == CaptureMode.VIDEO || isRecording) Color.Red else Color.White,
            ) {}
        }
    }
}
