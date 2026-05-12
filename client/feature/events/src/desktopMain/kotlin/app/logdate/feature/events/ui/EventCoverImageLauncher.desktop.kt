@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.events.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Desktop cover image picker backed by the platform file dialog.
 */
@Composable
actual fun rememberCoverImageLauncher(onPicked: (String?) -> Unit): () -> Unit =
    remember(onPicked) {
        {
            val dialog =
                FileDialog(null as Frame?, "Choose cover image", FileDialog.LOAD).apply {
                    setFilenameFilter { _, name ->
                        val extension = name.substringAfterLast('.', "").lowercase()
                        extension in SUPPORTED_EXTENSIONS
                    }
                    isVisible = true
                }
            val directory = dialog.directory
            val file = dialog.file
            onPicked(
                if (directory != null && file != null) {
                    File(directory, file).toURI().toString()
                } else {
                    null
                },
            )
        }
    }

private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
