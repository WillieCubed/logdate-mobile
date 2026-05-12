@file:OptIn(ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationAssetRepresentationModeCurrent
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.darwin.NSObject

@Composable
actual fun rememberCoverImageLauncher(onPicked: (String?) -> Unit): () -> Unit {
    var activeDelegate by remember { mutableStateOf<CoverImagePickerDelegate?>(null) }

    return remember(onPicked) {
        {
            val controller = topViewController()
            if (controller == null) {
                Napier.e("Unable to present iOS cover image picker: no root view controller")
                onPicked(null)
                return@remember
            }

            val delegate =
                CoverImagePickerDelegate(
                    onPicked = onPicked,
                    onDismiss = { activeDelegate = null },
                )
            activeDelegate = delegate

            val configuration =
                PHPickerConfiguration(photoLibrary = PHPhotoLibrary.sharedPhotoLibrary()).apply {
                    selectionLimit = 1
                    filter = PHPickerFilter.imagesFilter()
                    preferredAssetRepresentationMode = PHPickerConfigurationAssetRepresentationModeCurrent
                }
            val picker = PHPickerViewController(configuration = configuration)
            picker.delegate = delegate
            controller.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
    while (controller.presentedViewController != null) {
        controller = controller.presentedViewController!!
    }
    return controller
}

private class CoverImagePickerDelegate(
    private val onPicked: (String?) -> Unit,
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
        onPicked(assetIdentifier)
        onDismiss()
    }
}
