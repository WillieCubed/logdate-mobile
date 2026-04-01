@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:filename")

package app.logdate.feature.journals.ui.creation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame

@Composable
actual fun rememberMediaPickerLauncher(onMediaSelected: (List<String>) -> Unit): MediaPickerState =
    remember {
        MediaPickerState(
            launchAction = {
                val dialog = FileDialog(null as Frame?, "Select media", FileDialog.LOAD)
                dialog.isMultipleMode = true
                dialog.setFilenameFilter { _, name ->
                    val lower = name.lowercase()
                    lower.endsWith(".jpg") ||
                        lower.endsWith(".jpeg") ||
                        lower.endsWith(".png") ||
                        lower.endsWith(".gif") ||
                        lower.endsWith(".webp") ||
                        lower.endsWith(".mp4") ||
                        lower.endsWith(".mov")
                }
                dialog.isVisible = true
                val files = dialog.files
                if (files != null && files.isNotEmpty()) {
                    onMediaSelected(files.map { it.toURI().toString() })
                }
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
