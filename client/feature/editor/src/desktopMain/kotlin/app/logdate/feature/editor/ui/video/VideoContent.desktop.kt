package app.logdate.feature.editor.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.AspectRatios
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.add_a_video_to_your_entry
import logdate.client.feature.editor.generated.resources.choose_from_gallery
import logdate.client.feature.editor.generated.resources.play_video
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import java.nio.file.Paths
import javax.swing.SwingUtilities
import kotlin.io.path.absolutePathString

@Suppress("ktlint:standard:function-naming")
@Composable
actual fun VideoPlayerContent(
    uri: String,
    modifier: Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .aspectRatio(AspectRatios.WIDESCREEN)
                .clickable { openVideo(uri) },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.62f),
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(Res.string.play_video),
                modifier = Modifier.padding(18.dp).size(42.dp),
                tint = Color.White,
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
actual fun VideoPickerContent(
    onVideoSelected: (uri: String, durationMs: Long) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(AspectRatios.WIDESCREEN),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.add_a_video_to_your_entry),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            openVideoDialog { file ->
                                file?.let { selected ->
                                    onVideoSelected(selected.toURI().toString(), 0L)
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(Res.string.choose_from_gallery))
                }
            }
        }
    }
}

private fun openVideo(uri: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(uri))
        }
    }.onFailure { error ->
        Napier.e("Failed to open desktop video: $uri", error)
    }
}

private fun openVideoDialog(callback: (File?) -> Unit) {
    SwingUtilities.invokeLater {
        val fileDialog =
            FileDialog(Frame()).apply {
                title = "Select a Video"
                mode = FileDialog.LOAD
                isMultipleMode = false
                setFilenameFilter { _, name ->
                    val normalized = name.lowercase()
                    normalized.endsWith(".mp4") ||
                        normalized.endsWith(".mov") ||
                        normalized.endsWith(".m4v") ||
                        normalized.endsWith(".webm")
                }
            }

        fileDialog.isVisible = true

        val selectedFile =
            if (fileDialog.file != null) {
                File(Paths.get(fileDialog.directory, fileDialog.file).absolutePathString())
            } else {
                null
            }

        callback(selectedFile)
    }
}
