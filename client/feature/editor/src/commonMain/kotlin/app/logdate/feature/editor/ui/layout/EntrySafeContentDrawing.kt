package app.logdate.feature.editor.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Clip ink and controls, after their owning surface has painted behind system UI. */
@Composable
internal fun Modifier.clipEntryContentToSafeWindow(): Modifier {
    val insets = LocalEntryContentInsets.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val window = LocalWindowInfo.current.containerSize
    val origin = remember { mutableStateOf(Offset.Zero) }
    val fullyHidden = remember { mutableStateOf(false) }
    return onGloballyPositioned {
        val position = it.positionInWindow()
        origin.value = position
        fullyHidden.value = position.y + it.size.height <= insets.getTop(density) ||
            position.y >= window.height - insets.getBottom(density) ||
            position.x + it.size.width <= insets.getLeft(density, direction) ||
            position.x >= window.width - insets.getRight(density, direction)
    }.semantics { if (fullyHidden.value) hideFromAccessibility() }.graphicsLayer {
        // Read position in the layer phase: scrolling invalidates paint without remounting
        // content or recomposing the block on each frame.
        val position = origin.value
        val left = (insets.getLeft(density, direction) - position.x).coerceIn(0f, size.width)
        val top = (insets.getTop(density) - position.y).coerceIn(0f, size.height)
        val right = (window.width - insets.getRight(density, direction) - position.x).coerceIn(left, size.width)
        val bottom = (window.height - insets.getBottom(density) - position.y).coerceIn(top, size.height)
        shape = SafeContentClip(Rect(left, top, right, bottom))
        clip = true
    }
}

/** A real layer clip protects painting and pointer hit testing without changing layout. */
private class SafeContentClip(
    private val bounds: Rect,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rectangle(bounds)
}
