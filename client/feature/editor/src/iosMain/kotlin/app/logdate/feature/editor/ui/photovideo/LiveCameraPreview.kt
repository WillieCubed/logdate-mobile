package app.logdate.feature.editor.ui.photovideo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Suppress("ktlint:standard:function-naming")
@Composable
actual fun LiveCameraPreview(
    canUseCamera: Boolean,
    cameraType: CameraType,
    modifier: Modifier,
) {
    CameraUnavailablePanel(modifier = modifier)
}
