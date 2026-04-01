@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:filename")

package app.logdate.feature.journals.ui.creation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberMediaPickerLauncher(onMediaSelected: (List<String>) -> Unit): MediaPickerState =
    remember {
        MediaPickerState(
            launchAction = {
                // TODO: Implement PHPickerViewController integration for iOS
            },
        )
    }

actual class MediaPickerState(
    private val launchAction: () -> Unit,
) {
    actual fun launchPicker() {
        launchAction()
    }
}
