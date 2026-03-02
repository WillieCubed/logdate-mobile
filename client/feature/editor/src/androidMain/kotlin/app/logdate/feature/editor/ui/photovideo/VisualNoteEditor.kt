package app.logdate.feature.editor.ui.photovideo

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview

@Suppress("ktlint:standard:function-naming")
@Composable
internal actual fun VisualNoteEditor(cameraEnabled: Boolean) {
    val canUseCamera by remember {
        derivedStateOf { cameraEnabled }
    }
    Box {
        LiveCameraPreview(canUseCamera = canUseCamera, cameraType = CameraType.BACK)
    }
}

@Suppress("ktlint:standard:function-naming")
@Preview
@Composable
private fun VisualNoteEditorPreview() {
    NoteEditorSurface(false) {
        VisualNoteEditor()
    }
}
