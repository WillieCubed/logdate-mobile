package app.logdate.feature.editor.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import platform.UIKit.UIImagePickerControllerSourceType

/**
 * iOS implementation of the image picker content.
 * Provides the immersive gallery-only image entry point.
 */
@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun ImagePickerContent(
    onImageSelected: (String) -> Unit,
    modifier: Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    ImmersiveImagePickerEmptyState(
        onSelectImage = {
            coroutineScope.launch {
                showImagePicker(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary) { imageUrl ->
                    if (imageUrl != null) {
                        Napier.d("iOS photo library image selected: $imageUrl")
                        onImageSelected(imageUrl)
                    }
                }
            }
        },
        modifier = modifier,
    )
}

/**
 * Shows the iOS image picker with the specified source type.
 *
 * Note: This is a placeholder implementation that would need to be completed
 * with proper interop with UIImagePickerController and its delegate.
 *
 * @param sourceType The source type (camera or photo library)
 * @param onImageSelected Callback when an image is selected
 */
@OptIn(ExperimentalForeignApi::class)
private fun showImagePicker(
    sourceType: UIImagePickerControllerSourceType,
    onImageSelected: (String?) -> Unit,
) {
    Napier.i("iOS would show image picker with source type: $sourceType")
    onImageSelected(null)
}
