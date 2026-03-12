@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.camera

import android.Manifest
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.captured_photo
import logdate.client.feature.editor.generated.resources.captured_video
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
sealed interface CameraCapturePreviewState {
    data object PermissionRequired : CameraCapturePreviewState

    data class LiveCapture(
        val aspectRatio: CameraAspectRatio = CameraAspectRatio.STANDARD,
        val captureMode: CaptureMode = CaptureMode.PHOTO,
        val isRecording: Boolean = false,
    ) : CameraCapturePreviewState

    data class Review(
        val uri: String,
        val mediaType: CapturedMediaType,
    ) : CameraCapturePreviewState
}

@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun CameraCaptureContent(
    onMediaCaptured: (uri: String, mediaType: CapturedMediaType, durationMs: Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    if (LocalInspectionMode.current) {
        CameraCapturePreviewContent(
            state = CameraCapturePreviewState.LiveCapture(),
            modifier = modifier,
        )
        return
    }

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

    // Track review state locally — media is only committed on "Use"
    var pendingReview by remember { mutableStateOf<PendingReview?>(null) }

    LaunchedEffect(uiState.capturedMediaUri) {
        uiState.capturedMediaUri?.let { uri ->
            val mediaType = uiState.capturedMediaType ?: CapturedMediaType.PHOTO
            val duration = if (mediaType == CapturedMediaType.VIDEO) uiState.recordingDurationMs else 0L
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
            modifier = modifier,
        )
    }
}

@Composable
fun CameraCapturePreviewContent(
    state: CameraCapturePreviewState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CameraCapturePreviewState.PermissionRequired ->
            CameraPermissionRequest(
                onRequestPermission = {},
                modifier = modifier,
            )

        is CameraCapturePreviewState.LiveCapture ->
            PreviewInlineCameraCapture(
                aspectRatio = state.aspectRatio,
                captureMode = state.captureMode,
                isRecording = state.isRecording,
                modifier = modifier,
            )

        is CameraCapturePreviewState.Review ->
            MediaReviewContent(
                uri = state.uri,
                mediaType = state.mediaType,
                onRetake = {},
                onUse = {},
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

@Composable
private fun PreviewInlineCameraCapture(
    aspectRatio: CameraAspectRatio = CameraAspectRatio.STANDARD,
    captureMode: CaptureMode = CaptureMode.PHOTO,
    isRecording: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Viewfinder placeholder
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .aspectRatio(aspectRatio.ratio)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF101318)),
            )

            // Top controls — recording indicator
            if (isRecording) {
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "01:45",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // Bottom controls
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!isRecording) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AspectRatioSelector(
                            selected = aspectRatio,
                            onSelected = {},
                        )

                        PhotoVideoToggle(
                            currentMode = captureMode,
                            onModeChanged = {},
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Shutter row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.size(48.dp))

                    ShutterButton(
                        isRecording = isRecording,
                        captureMode = captureMode,
                        isCapturing = false,
                        onClick = {},
                    )

                    IconButton(
                        onClick = {},
                        enabled = !isRecording,
                        modifier =
                            Modifier
                                .background(
                                    MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f),
                                    CircleShape,
                                ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = stringResource(Res.string.switch_camera),
                            tint =
                                if (isRecording) {
                                    MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.38f)
                                } else {
                                    MaterialTheme.colorScheme.inverseOnSurface
                                },
                        )
                    }
                }
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
                        .navigationBarsPadding()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        ).padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.retake))
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

    // Camera switch overlay — fades to black during the switch so the user
    // sees a smooth transition instead of the viewfinder blinking.
    val switchOverlayAlpha = remember { Animatable(0f) }
    var isSwitchingCamera by remember { mutableStateOf(false) }

    // Capture flash state
    var showFlash by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isCapturing) {
        if (!uiState.isCapturing && showFlash) {
            delay(150)
            showFlash = false
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

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

    // Orchestrate camera switch: fade to black → switch → fade back
    LaunchedEffect(isSwitchingCamera) {
        if (isSwitchingCamera) {
            switchOverlayAlpha.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
            viewModel.switchCamera()
            // Brief hold so CameraX has time to emit the new surface request
            delay(200)
            switchOverlayAlpha.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
            isSwitchingCamera = false
        }
    }

    // Zoom state
    var currentZoom by remember { mutableFloatStateOf(1f) }

    // Viewfinder size for metering point calculations
    var viewfinderSize by remember { mutableStateOf(IntSize.Zero) }

    val animatedAspectRatio by animateFloatAsState(
        targetValue = uiState.aspectRatio.ratio,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "viewfinderAspectRatio",
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Camera viewfinder from the manager's surface request
            val currentSurfaceRequest = surfaceRequest
            AnimatedVisibility(
                visible = currentSurfaceRequest != null,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.Center),
            ) {
                // currentSurfaceRequest is captured at composition time;
                // AnimatedVisibility keeps rendering the exit animation after it becomes null.
                currentSurfaceRequest?.let { request ->
                    Box(
                        modifier =
                            Modifier
                                .aspectRatio(animatedAspectRatio)
                                .clip(RoundedCornerShape(20.dp))
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
                            surfaceRequest = request,
                            implementationMode = ImplementationMode.EXTERNAL,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Focus ring overlay
                        currentFocusPoint?.let { point ->
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val ringSize = 80f * focusRingScale.value
                                drawCircle(
                                    color = primaryColor.copy(alpha = focusRingAlpha.value),
                                    radius = ringSize / 2,
                                    center = point,
                                    style = Stroke(width = 2f),
                                )
                            }
                        }
                    }
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

            // Camera switch crossfade overlay
            if (switchOverlayAlpha.value > 0f) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = switchOverlayAlpha.value)),
                )
            }

            // Top controls — recording indicator only (back handled by editor toolbar)
            AnimatedVisibility(
                visible = uiState.isRecording,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = uiState.formattedDuration,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // Bottom controls
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = !uiState.isRecording,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AspectRatioSelector(
                            selected = uiState.aspectRatio,
                            onSelected = { viewModel.setAspectRatio(it) },
                        )

                        PhotoVideoToggle(
                            currentMode = uiState.captureMode,
                            onModeChanged = { viewModel.setCaptureMode(it) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Shutter row: empty spacer | shutter | switch camera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Balance spacer so shutter stays centered
                    Spacer(modifier = Modifier.size(48.dp))

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

                    IconButton(
                        onClick = {
                            if (!isSwitchingCamera) {
                                isSwitchingCamera = true
                                currentZoom = 1f
                            }
                        },
                        enabled = !uiState.isRecording && !isSwitchingCamera,
                        modifier =
                            Modifier
                                .background(
                                    MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f),
                                    CircleShape,
                                ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = stringResource(Res.string.switch_camera),
                            tint =
                                if (uiState.isRecording) {
                                    MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.38f)
                                } else {
                                    MaterialTheme.colorScheme.inverseOnSurface
                                },
                        )
                    }
                }
            }

            // Error display
            uiState.error?.let { error ->
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
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
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = currentMode == CaptureMode.PHOTO,
            onClick = { onModeChanged(CaptureMode.PHOTO) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = { SegmentedButtonDefaults.Icon(active = currentMode == CaptureMode.PHOTO) },
            label = { CameraToggleOption(Icons.Default.CameraAlt, stringResource(Res.string.photo)) },
        )

        SegmentedButton(
            selected = currentMode == CaptureMode.VIDEO,
            onClick = { onModeChanged(CaptureMode.VIDEO) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = { SegmentedButtonDefaults.Icon(active = currentMode == CaptureMode.VIDEO) },
            label = { CameraToggleOption(Icons.Default.Videocam, stringResource(Res.string.video)) },
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CameraToggleOption(
    icon: ImageVector,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Text(label)
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
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(innerSize),
                shape = if (isRecording) RoundedCornerShape(4.dp) else CircleShape,
                color =
                    if (captureMode == CaptureMode.VIDEO || isRecording) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            ) {}
        }
    }
}

/**
 * MD3 Expressive sliding-pill aspect ratio selector.
 *
 * A compact rounded container with three ratio labels. A filled indicator pill
 * slides behind the selected label with a smooth ease-out transition.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun AspectRatioSelector(
    selected: CameraAspectRatio,
    onSelected: (CameraAspectRatio) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = CameraAspectRatio.entries
    val selectedIndex = options.indexOf(selected)

    // Track each slot's x-offset and width so the indicator can slide to the right position.
    val slotOffsets = remember { mutableMapOf<Int, Float>() }
    val slotWidths = remember { mutableMapOf<Int, Float>() }

    val indicatorOffset by animateDpAsState(
        targetValue = (slotOffsets[selectedIndex] ?: 0f).pxToDp(),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "indicatorOffset",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = (slotWidths[selectedIndex] ?: 0f).pxToDp(),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "indicatorWidth",
    )

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.3f),
    ) {
        Box {
            // Sliding indicator pill
            if (indicatorWidth > 0.dp) {
                Surface(
                    modifier =
                        Modifier
                            .offset(x = indicatorOffset)
                            .size(width = indicatorWidth, height = 36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                ) {}
            }

            // Label slots
            Row {
                options.forEachIndexed { index, ratio ->
                    Box(
                        modifier =
                            Modifier
                                .clickable { onSelected(ratio) }
                                .onSizeChanged { size ->
                                    slotWidths[index] = size.width.toFloat()
                                }.onGloballyPositioned { coordinates ->
                                    slotOffsets[index] = coordinates.positionInParent().x
                                }.padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = ratio.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (ratio == selected) {
                                    MaterialTheme.colorScheme.inverseOnSurface
                                } else {
                                    MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f)
                                },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Converts a pixel value to [Dp] in a composable context.
 */
@Composable
private fun Float.pxToDp(): Dp {
    val density = LocalDensity.current
    return with(density) { this@pxToDp.toDp() }
}

/**
 * Holds captured media pending user review (retake/use decision).
 */
private data class PendingReview(
    val uri: String,
    val mediaType: CapturedMediaType,
    val durationMs: Long,
)
