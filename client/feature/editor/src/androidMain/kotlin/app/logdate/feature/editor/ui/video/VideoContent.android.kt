@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.video

import android.net.Uri
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
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
import logdate.client.feature.editor.generated.resources.pause_video
import logdate.client.feature.editor.generated.resources.play_video
import logdate.client.feature.editor.generated.resources.video_thumbnail
import org.jetbrains.compose.resources.stringResource

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

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isPlaying by remember { mutableStateOf(false) }
    var showThumbnail by remember { mutableStateOf(true) }

    val exoPlayer =
        remember {
            ExoPlayer.Builder(context).build().apply {
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
                    },
                )
            }
        }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        exoPlayer.pause()
                    }
                    Lifecycle.Event.ON_STOP -> {
                        exoPlayer.pause()
                    }
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .aspectRatio(16f / 9f),
    ) {
        // ExoPlayer view - always present for seamless playback
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
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
    }
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
                .aspectRatio(16f / 9f),
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
