package app.logdate.feature.editor.ui.newstuff

import androidx.compose.runtime.Composable

@Composable
expect fun LiveCameraPreview(
    canUseCamera: Boolean,
    lensDirection: LensDirection,
)

enum class LensDirection {
    FRONT,
    BACK,
}