package app.logdate.feature.editor.ui.newstuff

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
internal actual fun VisualNoteEditor(cameraEnabled: Boolean) {
    val canUseCamera by remember {
        derivedStateOf { cameraEnabled }
    }
    Box {
        LiveCameraPreview(canUseCamera = canUseCamera, lensDirection = LensDirection.BACK)
    }
}

@Preview
@Composable
private fun VisualNoteEditorPreview() {
    NoteEditorSurface {
        VisualNoteEditor()
    }
}
