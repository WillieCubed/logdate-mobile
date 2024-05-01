package app.logdate.feature.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import app.logdate.util.asTime
import app.logdate.util.localTime
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Composable
fun EntryEditor(
    state: EntryEditorState = rememberEntryEditorState()
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // TODO: Add entries from day

        item {
            // Header
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val color = MaterialTheme.colorScheme.onSurface
                Canvas(modifier = Modifier.width(16.dp)) {
                    // Draw a circle
                    drawCircle(
                        color,
                        radius = 8.dp.toPx(),
                        center = Offset(8.dp.toPx(), 8.dp.toPx())
                    )
                }
                Text(text = state.timestamp.localTime)
            }
        }
        item {
            Box(
                Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .fillMaxSize()
            ) {

            }
        }
    }
}

@Stable
data class EntryEditorState(
    val timestamp: Instant = Clock.System.now(),
    val textEditorState: TextEditorState,
    val audioRecorderState: AudioRecorderState,
    val cameraState: CameraState,
    val isInEditMode: Boolean = false,
) {
}

@Composable
fun rememberEntryEditorState(
    timestamp: Instant = Clock.System.now(),
    textEditorState: TextEditorState = rememberTextEditorState(),
    audioRecorderState: AudioRecorderState = rememberAudioRecorderState(),
    cameraState: CameraState = rememberCameraState(),
): EntryEditorState {
    return remember(
        timestamp,
        textEditorState,
        audioRecorderState,
        cameraState,
    ) {
        EntryEditorState(
            timestamp,
            textEditorState,
            audioRecorderState,
            cameraState,
        )
    }
}

@Stable
data class TextEditorState(
    val text: String = "",
    val isEditing: Boolean = false,
)

@Composable
fun rememberTextEditorState(): TextEditorState {
    return remember { TextEditorState("") }
}

@Composable
fun rememberAudioRecorderState(): AudioRecorderState {
    return remember { AudioRecorderState() }
}

@Stable
class AudioRecorderState() {

    val isRecording: Boolean = false

}

@Stable
class CameraState() {

    val isRecording: Boolean = false
    val cameraMode: CameraMode = CameraMode.PHOTO
}

@Composable
fun rememberCameraState(): CameraState {
    return remember { CameraState() }
}

enum class CameraMode {
    PHOTO,
    VIDEO
}

data class AudioPreviewData(
    val currentText: String,
    val isPlaying: Boolean,
    val canUseAudio: Boolean,
    val currentDuration: Long,
) {
    companion object {
        val Empty = AudioPreviewData(
            currentText = "",
            isPlaying = false,
            canUseAudio = false,
            currentDuration = 0L,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EntryEditorPreview() {
    LogDateTheme {
        EntryEditor()
    }
}