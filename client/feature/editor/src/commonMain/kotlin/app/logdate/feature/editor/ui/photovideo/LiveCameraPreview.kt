package app.logdate.feature.editor.ui.photovideo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A cross-platform composable that displays a live camera preview.
 *
 * @param canUseCamera Whether this composable is enabled (i.e. able to display a preview).
 * @param cameraType The direction of the camera lens to use.
 * @param modifier A modifier to apply to the layout root.
 */
@Composable
expect fun LiveCameraPreview(
    canUseCamera: Boolean,
    cameraType: CameraType,
    modifier: Modifier = Modifier,
)

enum class CameraType {
    FRONT,
    BACK,
}