package app.logdate.feature.editor.ui.image

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
 * Provides a button to open a file dialog to select an image.
 */
@Composable
actual fun ImagePickerContent(
    onImageSelected: (String) -> Unit,
    modifier: Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Add an image to your entry",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
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
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = "Select image",
                modifier = Modifier.padding(end = 8.dp)
            )
            Text("Select Image")
        }
    }
}

/**
 * Opens a native file dialog to select an image file.
 * 
 * @param callback Callback function that receives the selected file or null if canceled
 */
private fun openFileDialog(callback: (File?) -> Unit) {
    SwingUtilities.invokeLater {
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
        
        val selectedFile = if (fileDialog.file != null) {
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