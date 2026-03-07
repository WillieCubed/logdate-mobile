package app.logdate.feature.editor.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Paths
import javax.swing.SwingUtilities
import kotlin.io.path.absolutePathString

/**
 * Desktop implementation of the image picker content.
 * Provides an immersive gallery/file-library entry point.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
actual fun ImagePickerContent(
    onImageSelected: (String) -> Unit,
    modifier: Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    ImmersiveImagePickerEmptyState(
        onSelectImage = {
            coroutineScope.launch {
                openFileDialog { file ->
                    if (file != null) {
                        val uri = file.toURI().toString()
                        Napier.d("Desktop image selected: $uri")
                        onImageSelected(uri)
                    }
                }
            }
        },
        modifier = modifier,
    )
}

/**
 * Opens a native file dialog to select an image file.
 *
 * @param callback Callback function that receives the selected file or null if canceled
 */
private fun openFileDialog(callback: (File?) -> Unit) {
    SwingUtilities.invokeLater {
        val fileDialog =
            FileDialog(Frame()).apply {
                title = "Select an Image"
                mode = FileDialog.LOAD
                isMultipleMode = false

                setFilenameFilter { _, name ->
                    name.lowercase().endsWith(".jpg") ||
                        name.lowercase().endsWith(".jpeg") ||
                        name.lowercase().endsWith(".png") ||
                        name.lowercase().endsWith(".gif") ||
                        name.lowercase().endsWith(".bmp") ||
                        name.lowercase().endsWith(".webp")
                }
            }

        fileDialog.isVisible = true

        val selectedFile =
            if (fileDialog.file != null) {
                val directory = fileDialog.directory
                val filename = fileDialog.file
                val path = Paths.get(directory, filename)
                File(path.absolutePathString())
            } else {
                null
            }

        callback(selectedFile)
    }
}
