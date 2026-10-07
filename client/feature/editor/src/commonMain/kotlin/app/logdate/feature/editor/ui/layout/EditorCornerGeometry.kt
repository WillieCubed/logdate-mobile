package app.logdate.feature.editor.ui.layout

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.ui.platform.DefaultScreenCornerRadius

internal data class EditorCornerGeometry(
    val cardRadius: Dp,
    val controlRadius: Dp,
)

internal fun editorCornerGeometry(
    screenRadius: Dp,
    edgeInset: Dp = 16.dp,
    contentInset: Dp = 16.dp,
): EditorCornerGeometry {
    val radius = if (screenRadius.value.isFinite()) screenRadius else DefaultScreenCornerRadius
    val cardRadius = (radius - edgeInset).coerceAtLeast(8.dp)
    val controlRadius = (cardRadius - contentInset).coerceIn(8.dp, 16.dp)
    return EditorCornerGeometry(cardRadius, controlRadius)
}

internal val LocalEditorCorners = staticCompositionLocalOf { editorCornerGeometry(DefaultScreenCornerRadius) }
