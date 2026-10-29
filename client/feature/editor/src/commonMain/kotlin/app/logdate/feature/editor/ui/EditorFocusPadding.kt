package app.logdate.feature.editor.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import kotlin.math.roundToInt

internal data class EditorFocusInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

/** Endpoint measurement stays exact; foreground spacing follows the surface during approach. */
internal fun Modifier.editorFocusPadding(
    inline: EditorFocusInsets = EditorFocusInsets(),
    focused: EditorFocusInsets = EditorFocusInsets(),
    expanded: Boolean,
    progress: Float,
): Modifier =
    layout { measurable, constraints ->
        val fraction =
            if (isLookingAhead) {
                if (expanded) {
                    1f
                } else {
                    0f
                }
            } else {
                progress.coerceIn(0f, 1f)
            }

        fun edge(
            start: Int,
            end: Int,
        ) = (start + (end - start) * fraction).roundToInt()
        val left = edge(inline.left, focused.left)
        val right = edge(inline.right, focused.right)
        val top = edge(inline.top, focused.top)
        val bottom = edge(inline.bottom, focused.bottom)
        val child = measurable.measure(constraints.offset(-left - right, -top - bottom))
        layout(constraints.constrainWidth(child.width + left + right), constraints.constrainHeight(child.height + top + bottom)) {
            child.place(left, top)
        }
    }
