@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.logdate.client.media.device.AudioRouteRepository
import app.logdate.client.media.video.ExoPlayerPool
import app.logdate.ui.media.MediaDeviceSelector
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.videoFramePercent
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.add_a_video_to_your_entry
import logdate.client.feature.editor.generated.resources.choose_from_gallery
import logdate.client.feature.editor.generated.resources.enter_picture_in_picture
import logdate.client.feature.editor.generated.resources.pause_video
import logdate.client.feature.editor.generated.resources.play_video
import logdate.client.feature.editor.generated.resources.video_thumbnail
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Android implementation of video player content.
 * Uses ExoPlayer for production-quality video playback with proper lifecycle management.
 * Shows a thumbnail with play button when paused, transitions to full player on play.
 */
@Suppress("ktlint:standard:function-naming")
@OptIn(UnstableApi::class)
@Composable
actual fun VideoPlayerContent(
    uri: String,
    modifier: Modifier,
) {
    if (LocalInspectionMode.current) {
        PreviewVideoPlayerContent(
            previewThumbnailModel = uri,
            modifier = modifier,
        )
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val exoPlayerPool: ExoPlayerPool = koinInject()
    val audioRouteRepository: AudioRouteRepository = koinInject()
    val outputSelection by audioRouteRepository.outputDevices.collectAsState()

    var isPlaying by remember { mutableStateOf(false) }
    var showThumbnail by remember { mutableStateOf(true) }
    // Default to a landscape frame while metadata loads; the player updates this
    // as soon as it knows the real dimensions so portrait videos don't get
    // cropped into a 16:9 box.
    var videoAspectRatio by remember { mutableStateOf(DEFAULT_VIDEO_ASPECT_RATIO) }
    val pipAspectRatio = remember(videoAspectRatio) { videoAspectRatio.toPictureInPictureRatio() }

    // Acquire a warm player from the pool instead of rebuilding renderers on
    // every video tile that scrolls into view. The pool also wires the shared
    // disk cache so a video the user already opened once replays instantly.
    val exoPlayer =
        remember(uri) {
            exoPlayerPool.acquire().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = false
                addListener(
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_ENDED -> {
                                    seekTo(0)
                                    pause()
                                    isPlaying = false
                                    showThumbnail = true
                                }
                                Player.STATE_READY -> {
                                    if (isPlaying) {
                                        showThumbnail = false
                                    }
                                }
                            }
                        }

                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                            if (playing) {
                                showThumbnail = false
                            }
                        }

                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            videoAspectRatio = videoSize.toAspectRatioOrDefault()
                        }
                    },
                )
            }
        }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        if (activity?.isInPictureInPictureMode != true) {
                            exoPlayer.pause()
                        }
                    }
                    Lifecycle.Event.ON_STOP -> {
                        if (activity?.isInPictureInPictureMode != true) {
                            exoPlayer.pause()
                        }
                    }
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayerPool.release(exoPlayer)
        }
    }

    DisposableEffect(activity, isPlaying, pipAspectRatio) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
            activity.setPictureInPictureParams(
                buildVideoPictureInPictureParams(
                    aspectRatio = pipAspectRatio,
                    autoEnterEnabled = isPlaying,
                ),
            )
        }

        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
                activity.setPictureInPictureParams(
                    buildVideoPictureInPictureParams(
                        aspectRatio = pipAspectRatio,
                        autoEnterEnabled = false,
                    ),
                )
            }
        }
    }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .aspectRatio(videoAspectRatio),
    ) {
        // ExoPlayer view - always present for seamless playback
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    // FIT keeps the whole frame on screen; ZOOM would center-crop
                    // and chop the top and bottom off a portrait video.
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Thumbnail overlay - shown when not playing
        if (showThumbnail) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable {
                            showThumbnail = false
                            exoPlayer.play()
                        },
            ) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalPlatformContext.current)
                            .data(uri)
                            .crossfade(true)
                            .videoFramePercent(0.1)
                            .build(),
                    contentDescription = stringResource(Res.string.video_thumbnail),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                // Play button overlay
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier =
                        Modifier
                            .size(64.dp)
                            .align(Alignment.Center),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(Res.string.play_video),
                            modifier = Modifier.size(36.dp),
                            tint = Color.Black,
                        )
                    }
                }
            }
        } else {
            // Pause button overlay when playing
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable {
                            if (isPlaying) {
                                exoPlayer.pause()
                                showThumbnail = true
                            } else {
                                exoPlayer.play()
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                // Semi-transparent pause indicator (briefly visible on tap)
                if (!isPlaying) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = stringResource(Res.string.pause_video),
                                modifier = Modifier.size(36.dp),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }

        MediaDeviceSelector(
            selection = outputSelection,
            onDeviceSelected = audioRouteRepository::selectOutputDevice,
            label = "Audio output",
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
        )

        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            IconButton(
                onClick = {
                    showThumbnail = false
                    exoPlayer.play()
                    enterVideoPictureInPicture(
                        activity = activity,
                        aspectRatio = pipAspectRatio,
                    )
                },
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                            shape = CircleShape,
                        ),
            ) {
                Icon(
                    imageVector = Icons.Default.PictureInPictureAlt,
                    contentDescription = stringResource(Res.string.enter_picture_in_picture),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun enterVideoPictureInPicture(
    activity: Activity,
    aspectRatio: Rational,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    try {
        activity.enterPictureInPictureMode(
            buildVideoPictureInPictureParams(
                aspectRatio = aspectRatio,
                autoEnterEnabled = false,
            ),
        )
    } catch (e: IllegalStateException) {
        Napier.w("Could not enter video picture-in-picture mode", e)
    } catch (e: IllegalArgumentException) {
        Napier.w("Could not enter video picture-in-picture mode with ratio $aspectRatio", e)
    }
}

private fun buildVideoPictureInPictureParams(
    aspectRatio: Rational,
    autoEnterEnabled: Boolean,
): PictureInPictureParams {
    val builder =
        PictureInPictureParams
            .Builder()
            .setAspectRatio(aspectRatio)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(autoEnterEnabled)
    }

    return builder.build()
}

private fun Float.toPictureInPictureRatio(): Rational {
    val boundedRatio = coerceIn(MIN_PIP_ASPECT_RATIO, MAX_PIP_ASPECT_RATIO)
    val width = (boundedRatio * PIP_RATIO_DENOMINATOR).toInt().coerceAtLeast(1)
    return Rational(width, PIP_RATIO_DENOMINATOR)
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * Android implementation of video picker content.
 * Provides option to select a video from the gallery with proper permission handling.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
actual fun VideoPickerContent(
    onVideoSelected: (uri: String, durationMs: Long) -> Unit,
    modifier: Modifier,
) {
    if (LocalInspectionMode.current) {
        VideoPickerPreviewContent(modifier = modifier)
        return
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val videoPickerLauncher =
        rememberLauncherForActivityResult(
            // The system photo picker runs in its own process, so it needs no
            // storage permission. This keeps the "browse all" path zero-prompt
            // and only surfaces videos, not arbitrary documents.
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri: Uri? ->
            uri?.let {
                Napier.d("Video selected: $uri")
                // Duration extraction goes through MediaMetadataRetriever, which
                // can block for hundreds of milliseconds on cloud-backed URIs.
                // Read it off the main thread so the picker dismiss animation
                // stays smooth.
                coroutineScope.launch {
                    val duration = getVideoDuration(context, uri)
                    onVideoSelected(uri.toString(), duration)
                }
            }
        }

    VideoPickerCard(
        onChooseFromGallery = {
            videoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
            )
        },
        modifier = modifier,
    )
}

@Composable
fun VideoPickerPreviewContent(modifier: Modifier = Modifier) {
    VideoPickerCard(
        onChooseFromGallery = {},
        modifier = modifier,
    )
}

@Composable
private fun VideoPickerCard(
    onChooseFromGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .height(200.dp),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.add_a_video_to_your_entry),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = onChooseFromGallery,
            ) {
                Text(stringResource(Res.string.choose_from_gallery))
            }
        }
    }
}

@Composable
private fun PreviewVideoPlayerContent(
    previewThumbnailModel: Any?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .aspectRatio(DEFAULT_VIDEO_ASPECT_RATIO),
    ) {
        if (previewThumbnailModel != null) {
            AsyncImage(
                model = previewThumbnailModel,
                contentDescription = stringResource(Res.string.video_thumbnail),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }

        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.9f),
            modifier =
                Modifier
                    .size(64.dp)
                    .align(Alignment.Center),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(Res.string.play_video),
                    modifier = Modifier.size(36.dp),
                    tint = Color.Black,
                )
            }
        }
    }
}

/**
 * Default aspect ratio used while the player is still resolving real video
 * dimensions. Landscape 16:9 is a sensible neutral framing for journal videos.
 */
private const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f
private const val MIN_PIP_ASPECT_RATIO = 1f / 2.39f
private const val MAX_PIP_ASPECT_RATIO = 2.39f
private const val PIP_RATIO_DENOMINATOR = 10_000

/**
 * Resolves a [VideoSize] to a display-aspect ratio that accounts for any
 * non-square pixels (anamorphic content), falling back to
 * [DEFAULT_VIDEO_ASPECT_RATIO] if dimensions are not yet known.
 */
private fun VideoSize.toAspectRatioOrDefault(): Float {
    if (width <= 0 || height <= 0) return DEFAULT_VIDEO_ASPECT_RATIO
    val displayWidth = width.toFloat() * pixelWidthHeightRatio
    val displayHeight = height.toFloat()
    return if (displayHeight > 0f) displayWidth / displayHeight else DEFAULT_VIDEO_ASPECT_RATIO
}

/**
 * Reads a video's duration in milliseconds via [android.media.MediaMetadataRetriever].
 *
 * [android.media.MediaMetadataRetriever.setDataSource] performs blocking IO and
 * can stall for hundreds of milliseconds — multiple seconds on cloud-backed
 * content URIs — so this is suspending and dispatched to [Dispatchers.IO].
 */
private suspend fun getVideoDuration(
    context: android.content.Context,
    uri: Uri,
): Long =
    withContext(Dispatchers.IO) {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever
                .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Napier.e("Failed to get video duration", e)
            0L
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Napier.e("Failed to release MediaMetadataRetriever", e)
            }
        }
    }
