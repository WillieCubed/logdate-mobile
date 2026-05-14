@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.video

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import app.logdate.client.media.IosMediaManager
import app.logdate.client.media.MediaManager
import app.logdate.ui.common.AspectRatios
import io.github.aakira.napier.Napier
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.add_a_video_to_your_entry
import logdate.client.feature.editor.generated.resources.choose_from_gallery
import logdate.client.feature.editor.generated.resources.pause_video
import logdate.client.feature.editor.generated.resources.play_video
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setVolume
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationAssetRepresentationModeCurrent
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIView
import platform.darwin.NSObject

private const val CONTROLS_AUTO_HIDE_MS = 3000L
private const val POSITION_POLL_MS = 250L

@Composable
actual fun VideoPlayerContent(
    uri: String,
    modifier: Modifier,
) {
    val url = remember(uri) { NSURL.URLWithString(uri) }
    if (url == null) {
        Box(modifier = modifier.background(Color.Black))
        return
    }

    val player: AVPlayer = remember(uri) { AVPlayer(uRL = url) }
    val playerLayer = remember(uri) { AVPlayerLayer.playerLayerWithPlayer(player) }

    var isPlaying by remember(uri) { mutableStateOf(false) }
    var isMuted by remember(uri) { mutableStateOf(false) }
    var positionMs by remember(uri) { mutableLongStateOf(0L) }
    var durationMs by remember(uri) { mutableLongStateOf(0L) }
    var controlsVisible by remember(uri) { mutableStateOf(true) }
    var lastInteractionTick by remember(uri) { mutableLongStateOf(0L) }

    // Resolve total duration once the asset loads.
    LaunchedEffect(uri) {
        val asset = AVURLAsset.URLAssetWithURL(url, options = null)
        durationMs = (asset.duration.useContents { value.toDouble() / timescale.toDouble() } * 1000.0).toLong()
    }

    // Poll AVPlayer for current time. Cheaper than addPeriodicTimeObserver for
    // a UI that already recomposes per state change; 250ms keeps the slider
    // smooth without burning the CPU.
    LaunchedEffect(uri, isPlaying) {
        while (isPlaying) {
            positionMs =
                (player.currentTime().useContents { value.toDouble() / timescale.toDouble() } * 1000.0).toLong()
            delay(POSITION_POLL_MS)
        }
    }

    // Auto-hide controls after a beat of inactivity.
    LaunchedEffect(lastInteractionTick) {
        if (controlsVisible) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    // Pause when the composable leaves to avoid orphan audio.
    DisposableEffect(uri) {
        onDispose {
            player.pause()
        }
    }

    val playPauseLabel =
        stringResource(if (isPlaying) Res.string.pause_video else Res.string.play_video)

    Box(
        modifier =
            modifier
                .aspectRatio(AspectRatios.WIDESCREEN)
                .background(Color.Black)
                .clickable {
                    controlsVisible = !controlsVisible
                    if (controlsVisible) lastInteractionTick = lastInteractionTick + 1
                },
    ) {
        UIKitView(
            factory = {
                UIView().also { view ->
                    view.backgroundColor = platform.UIKit.UIColor.blackColor
                    view.layer.addSublayer(playerLayer)
                }
            },
            update = { view ->
                view.bounds.useContents {
                    playerLayer.frame = CGRectMake(0.0, 0.0, size.width, size.height)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(modifier = Modifier.height(1.dp))

                // Bottom strip: scrubber + play/pause + mute.
                Column(modifier = Modifier.fillMaxWidth()) {
                    val sliderMax = (durationMs.coerceAtLeast(1L)).toFloat()
                    Slider(
                        value = positionMs.coerceIn(0L, durationMs).toFloat(),
                        onValueChange = { newMs ->
                            positionMs = newMs.toLong()
                            lastInteractionTick = lastInteractionTick + 1
                        },
                        onValueChangeFinished = {
                            val seconds = positionMs / 1000.0
                            val target = CMTimeMakeWithSeconds(seconds, preferredTimescale = 600)
                            player.seekToTime(target)
                        },
                        valueRange = 0f..sliderMax,
                        colors =
                            SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    player.pause()
                                    isPlaying = false
                                } else {
                                    player.play()
                                    isPlaying = true
                                }
                                lastInteractionTick = lastInteractionTick + 1
                            },
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = playPauseLabel,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }

                        Text(
                            text = formatTimestamp(positionMs) + " / " + formatTimestamp(durationMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                        )

                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                player.setVolume(if (isMuted) 0f else 1f)
                                lastInteractionTick = lastInteractionTick + 1
                            },
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return minutes.toString() + ":" + seconds.toString().padStart(2, '0')
}

@Composable
actual fun VideoPickerContent(
    onVideoSelected: (uri: String, durationMs: Long) -> Unit,
    modifier: Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val mediaManager = koinInject<MediaManager>() as? IosMediaManager
    var activeDelegate by remember { mutableStateOf<VideoPickerDelegate?>(null) }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(AspectRatios.WIDESCREEN),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.add_a_video_to_your_entry),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        val presentingController = topViewController()
                        if (presentingController == null) {
                            Napier.w("Unable to present iOS video picker because no root view controller was found")
                            return@OutlinedButton
                        }

                        val delegate =
                            VideoPickerDelegate(
                                onAssetSelected = { localIdentifier ->
                                    coroutineScope.launch {
                                        val resolvedUri = mediaManager?.resolvePhotoLibraryVideoUri(localIdentifier)
                                        activeDelegate = null
                                        if (resolvedUri != null) {
                                            onVideoSelected(resolvedUri, resolveDurationMs(resolvedUri))
                                        }
                                    }
                                },
                                onDismiss = { activeDelegate = null },
                            )
                        activeDelegate = delegate

                        val configuration =
                            PHPickerConfiguration(photoLibrary = PHPhotoLibrary.sharedPhotoLibrary()).apply {
                                selectionLimit = 1
                                filter = PHPickerFilter.videosFilter()
                                preferredAssetRepresentationMode = PHPickerConfigurationAssetRepresentationModeCurrent
                            }
                        PHPickerViewController(configuration = configuration).also { picker ->
                            picker.delegate = delegate
                            presentingController.presentViewController(picker, animated = true, completion = null)
                        }
                    },
                ) {
                    Text(stringResource(Res.string.choose_from_gallery))
                }
            }
        }
    }
}

private fun resolveDurationMs(uri: String): Long {
    val url = NSURL.URLWithString(uri) ?: return 0L
    val asset = AVURLAsset.URLAssetWithURL(url, options = null)
    return (asset.duration.useContents { value.toDouble() / timescale.toDouble() } * 1000.0).toLong()
}

private fun topViewController(): platform.UIKit.UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
    while (controller.presentedViewController != null) {
        controller = controller.presentedViewController!!
    }
    return controller
}

private class VideoPickerDelegate(
    private val onAssetSelected: (String) -> Unit,
    private val onDismiss: () -> Unit,
) : NSObject(),
    PHPickerViewControllerDelegateProtocol {
    override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>,
    ) {
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        val assetIdentifier = result?.assetIdentifier

        picker.dismissViewControllerAnimated(true, completion = null)

        if (assetIdentifier.isNullOrBlank()) {
            onDismiss()
            return
        }

        onAssetSelected(assetIdentifier)
    }
}
