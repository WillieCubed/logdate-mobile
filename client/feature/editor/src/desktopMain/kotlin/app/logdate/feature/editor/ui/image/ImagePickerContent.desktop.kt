package app.logdate.feature.editor.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.logdate.client.media.MediaManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.editor_media_select_an_image_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Paths
import javax.swing.SwingUtilities
import kotlin.io.path.absolutePathString

@Suppress("ktlint:standard:function-naming")
@Composable
actual fun ImagePickerContent(
    onImageSelected: (String) -> Unit,
    modifier: Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val mediaManager: MediaManager = koinInject()
    val fileDialogTitle = stringResource(Res.string.editor_media_select_an_image_title)

    var reloadTrigger by remember { mutableIntStateOf(0) }
    var libraryState by remember { mutableStateOf<ImagePickerLibraryState>(ImagePickerLibraryState.Loading) }

    fun loadRecentImages() {
        coroutineScope.launch {
            libraryState = ImagePickerLibraryState.Loading

            libraryState =
                try {
                    val images = recentImagePreviews(mediaManager.getRecentMedia().first())
                    if (images.isEmpty()) {
                        ImagePickerLibraryState.Empty
                    } else {
                        ImagePickerLibraryState.Loaded(images)
                    }
                } catch (error: Exception) {
                    Napier.e("Failed to load recent Desktop images", error)
                    ImagePickerLibraryState.Error
                }
        }
    }

    LaunchedEffect(reloadTrigger) {
        loadRecentImages()
    }

    ImagePickerBrowser(
        state = libraryState,
        onImageSelected = onImageSelected,
        onBrowseLibrary = {
            coroutineScope.launch {
                openFileDialog(fileDialogTitle) { file ->
                    file?.let {
                        val uri = it.toURI().toString()
                        Napier.d("Desktop image selected: $uri")
                        onImageSelected(uri)
                    }
                }
            }
        },
        onRetryLoading = { reloadTrigger++ },
        modifier = modifier,
    )
}

private fun openFileDialog(
    dialogTitle: String,
    callback: (File?) -> Unit,
) {
    SwingUtilities.invokeLater {
        val fileDialog =
            FileDialog(Frame()).apply {
                title = dialogTitle
                mode = FileDialog.LOAD
                isMultipleMode = false

                setFilenameFilter { _, name ->
                    name.lowercase().endsWith(".jpg") ||
                        name.lowercase().endsWith(".jpeg") ||
                        name.lowercase().endsWith(".png") ||
                        name.lowercase().endsWith(".gif") ||
                        name.lowercase().endsWith(".bmp") ||
                        name.lowercase().endsWith(".webp") ||
                        name.lowercase().endsWith(".heic") ||
                        name.lowercase().endsWith(".heif")
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
