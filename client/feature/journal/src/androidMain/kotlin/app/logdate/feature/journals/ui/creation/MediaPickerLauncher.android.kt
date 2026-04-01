@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:filename")

package app.logdate.feature.journals.ui.creation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberMediaPickerLauncher(onMediaSelected: (List<String>) -> Unit): MediaPickerState {
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(),
        ) { uris ->
            onMediaSelected(uris.map { it.toString() })
        }

    return remember(launcher) {
        MediaPickerState(
            launchAction = {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
        )
    }
}

actual class MediaPickerState(
    private val launchAction: () -> Unit,
) {
    actual fun launchPicker() {
        launchAction()
    }
}
