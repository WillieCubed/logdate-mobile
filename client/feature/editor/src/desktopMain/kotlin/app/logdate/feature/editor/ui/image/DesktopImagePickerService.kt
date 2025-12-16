package app.logdate.feature.editor.ui.image

import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.coroutines.resume

/**
 * Desktop implementation of the ImagePickerService.
 * 
 * This implementation provides methods to select images from the file system.
 * Camera capture is not supported on desktop platforms.
 */
class DesktopImagePickerService : ImagePickerService {
    
    /**
     * Opens a file dialog to select an image file.
     * 
     * @return The URI of the selected image file, or null if selection was cancelled
     */
    override suspend fun pickImage(): String? = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val fileDialog = FileDialog(Frame()).apply {
                    title = "Select an Image"
                    mode = FileDialog.LOAD
                    isMultipleMode = false
                    
                    // Set file filter for images
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
                
                val result = if (fileDialog.file != null) {
                    val directory = fileDialog.directory
                    val filename = fileDialog.file
                    val file = File(directory, filename)
                    file.toURI().toString()
                } else {
                    null
                }
                
                continuation.resume(result)
            } catch (e: Exception) {
                Napier.e("DesktopImagePickerService: Error picking image", e)
                continuation.resume(null)
            }
        }
    }
    
    /**
     * Camera capture is not supported on desktop platforms.
     * 
     * @return Always returns null
     */
    override suspend fun captureImage(): String? {
        Napier.w("DesktopImagePickerService: Camera capture is not supported on desktop platforms")
        return null
    }
}