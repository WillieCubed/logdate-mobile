package app.logdate.feature.editor.ui.photovideo

import androidx.compose.runtime.Composable

@Suppress("ktlint:standard:function-naming")
@Composable
internal actual fun VisualNoteEditor(cameraEnabled: Boolean) {
    LiveCameraPreview(
        canUseCamera = false,
        cameraType = CameraType.BACK,
    )
}
