package app.logdate.feature.editor.ui.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

/**
 * A canvas editor that allows for the manipulation of objects on a canvas.
 */
interface CanvasEditor {
    fun updatePosition(objectId: String, position: Position)
}

interface CanvasObject {
    val objectId: String
    val position: Position
}

data class Position(
    val x: Int,
    val y: Int,
)

@Stable
class CanvasState(
    val objects: List<CanvasObject>,
) : CanvasEditor {
    override fun updatePosition(objectId: String, position: Position) {

    }
}

// TODO: Implement Instagram Stories-like canvas editor
@Composable
fun rememberCanvasState(): CanvasState {
    return remember {
        CanvasState(emptyList())
    }
}