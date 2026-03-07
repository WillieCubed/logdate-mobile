package app.logdate.feature.editor.ui.camera

import android.Manifest
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.captured_photo
import logdate.client.feature.editor.generated.resources.captured_video
import logdate.client.feature.editor.generated.resources.close_camera
import logdate.client.feature.editor.generated.resources.loading_camera
import logdate.client.feature.editor.generated.resources.photo
import logdate.client.feature.editor.generated.resources.retake
import logdate.client.feature.editor.generated.resources.switch_camera
import logdate.client.feature.editor.generated.resources.tap_to_enable_camera
import logdate.client.feature.editor.generated.resources.use_photo
import logdate.client.feature.editor.generated.resources.use_video
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

    val hasPermissions = cameraPermissions.allPermissionsGranted

    LaunchedEffect(cameraPermissions.permissions) {
        Napier.d(
            "Camera permissions state: ${cameraPermissions.permissions.map {
                "${it.permission}: ${it.status}"
            }}",
        )
    }

    // Track review state locally — media is only committed on "Use"
    var pendingReview by remember { mutableStateOf<PendingReview?>(null) }

    LaunchedEffect(uiState.capturedMediaUri) {
        uiState.capturedMediaUri?.let { uri ->
            val mediaType = uiState.capturedMediaType ?: CapturedMediaType.PHOTO
            val duration = if (mediaType == CapturedMediaType.VIDEO) uiState.recordingDurationMs else 0L
            Napier.d("CameraCaptureContent - Captured media: $uri, type: $mediaType, duration: $duration")
            pendingReview = PendingReview(uri, mediaType, duration)
            viewModel.clearCapturedMedia()
        }
    }

    if (!hasPermissions) {
        CameraPermissionRequest(
            onRequestPermission = { cameraPermissions.launchMultiplePermissionRequest() },
            modifier = modifier,
        )
    } else if (pendingReview != null) {
        val review = pendingReview!!
        MediaReviewContent(
            uri = review.uri,
            mediaType = review.mediaType,
            onRetake = { pendingReview = null },
            onUse = {
                onMediaCaptured(review.uri, review.mediaType, review.durationMs)
                pendingReview = null
            },
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
 * Review screen shown after capturing media.
 * Lets the user retake or confirm before committing to the journal entry.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun MediaReviewContent(
    uri: String,
    mediaType: CapturedMediaType,
    onRetake: () -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentDesc =
        stringResource(
            when (mediaType) {
                CapturedMediaType.PHOTO -> Res.string.captured_photo
                CapturedMediaType.VIDEO -> Res.string.captured_video
            },
        )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalPlatformContext.current)
                        .data(uri)
                        .crossfade(true)
                        .build(),
                contentDescription = contentDesc,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            // Bottom action bar
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.retake), color = Color.White)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = onUse,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            when (mediaType) {
                                CapturedMediaType.PHOTO -> Res.string.use_photo
                                CapturedMediaType.VIDEO -> Res.string.use_video
                            },
                        ),
                    )
                }
            }
        }
    }
}

/**
 * First-class inline camera capture interface with live preview and controls.
 * Uses the [AndroidCameraCaptureManager]'s surface request directly to avoid
 * conflicting camera bindings.
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
    val haptic = LocalHapticFeedback.current

    val manager = viewModel.getCaptureManager() as? AndroidCameraCaptureManager

    // Collect the surface request from the manager
    val surfaceRequest by manager?.surfaceRequest?.collectAsState()
        ?: remember { mutableStateOf(null) }

    // Start preview every time this composable enters composition.
    // LaunchedEffect(Unit) re-fires on each fresh composition entry (after retake, reopen, etc.)
    // and its coroutine is cancelled when the composable leaves.
    LaunchedEffect(Unit) {
        manager?.setLifecycleOwner(lifecycleOwner)
        viewModel.startPreview()
    }

    // Stop preview when this composable leaves composition (review screen, block delete, etc.)
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPreview()
        }
    }

    // Capture flash state
    var showFlash by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isCapturing) {
        if (!uiState.isCapturing && showFlash) {
            delay(150)
            showFlash = false
        }
    }

    // Focus ring state
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val focusRingAlpha = remember { Animatable(0f) }
    val focusRingScale = remember { Animatable(0f) }

    val currentFocusPoint = focusPoint
    LaunchedEffect(currentFocusPoint) {
        if (currentFocusPoint != null) {
            focusRingAlpha.snapTo(1f)
            focusRingScale.snapTo(1.5f)
            focusRingScale.animateTo(1f, tween(200))
            delay(800)
            focusRingAlpha.animateTo(0f, tween(300))
        }
    }

    // Zoom state
    var currentZoom by remember { mutableFloatStateOf(1f) }

    // Viewfinder size for metering point calculations
    var viewfinderSize by remember { mutableStateOf(IntSize.Zero) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Camera viewfinder from the manager's surface request
            val currentSurfaceRequest = surfaceRequest
            if (currentSurfaceRequest != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(12.dp))
                            .onSizeChanged { viewfinderSize = it }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    currentZoom = (currentZoom * zoom).coerceIn(1f, 10f)
                                    manager?.setZoomRatio(currentZoom)
                                }
                            }.pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        focusPoint = offset
                                        if (viewfinderSize.width > 0 && viewfinderSize.height > 0) {
                                            val factory =
                                                SurfaceOrientedMeteringPointFactory(
                                                    viewfinderSize.width.toFloat(),
                                                    viewfinderSize.height.toFloat(),
                                                )
                                            manager?.tapToFocus(factory, offset.x, offset.y)
                                        }
                                    },
                                )
                            },
                ) {
                    CameraXViewfinder(
                        surfaceRequest = currentSurfaceRequest,
                        implementationMode = ImplementationMode.EXTERNAL,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Focus ring overlay
                    currentFocusPoint?.let { point ->
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val ringSize = 80f * focusRingScale.value
                            drawCircle(
                                color = Color.White.copy(alpha = focusRingAlpha.value),
                                radius = ringSize / 2,
                                center = point,
                                style = Stroke(width = 2f),
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.loading_camera),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Capture flash overlay
            AnimatedVisibility(
                visible = showFlash,
                enter = fadeIn(tween(50)),
                exit = fadeOut(tween(100)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.White),
                )
            }

            // Top controls row
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
                        onClick = {
                            currentZoom = 1f
                            viewModel.switchCamera()
                        },
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

            // Bottom controls
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
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (uiState.captureMode == CaptureMode.PHOTO) {
                            showFlash = true
                        }
                        viewModel.capture()
                    },
                )
            }

            // Error display
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
        val toggleColors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color.White,
                selectedLabelColor = Color.Black,
                selectedLeadingIconColor = Color.Black,
                containerColor = Color.Transparent,
                labelColor = Color.White,
                iconColor = Color.White,
            )

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
            colors = toggleColors,
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
            colors = toggleColors,
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

/**
 * Holds captured media pending user review (retake/use decision).
 */
private data class PendingReview(
    val uri: String,
    val mediaType: CapturedMediaType,
    val durationMs: Long,
)
