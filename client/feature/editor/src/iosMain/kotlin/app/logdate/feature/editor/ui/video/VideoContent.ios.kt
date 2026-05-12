@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.video

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.launch
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.add_a_video_to_your_entry
import logdate.client.feature.editor.generated.resources.choose_from_gallery
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVURLAsset
import platform.CoreGraphics.CGRectMake
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
        modifier =
            modifier
                .aspectRatio(AspectRatios.WIDESCREEN)
                .background(Color.Black),
    )
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
