package app.logdate.feature.editor.ui.image

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aakira.napier.Napier

/**
 * Android implementation of the image picker content.
 *
 * Uses the system photo picker so gallery selection works without requesting
 * legacy storage permissions inside the editor flow.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
actual fun ImagePickerContent(
    onImageSelected: (String) -> Unit,
    modifier: Modifier,
) {
    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            uri?.let {
                Napier.d("Android image selected: $it")
                onImageSelected(it.toString())
            } ?: Napier.d("Android image picker dismissed without a selection")
        }

    ImmersiveImagePickerEmptyState(
        onSelectImage = {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        modifier = modifier,
    )
}
