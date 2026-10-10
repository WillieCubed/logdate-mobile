package app.logdate.ui.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect

/**
 * The window's caption bar: the title bar Android desktop windowing draws above each window, with
 * the app's name and the minimize, maximize, and close buttons. An app may draw its own controls in
 * the rest of the bar.
 *
 * [left] and [right] are the widths the system's own controls take at each edge of the window. They
 * are absolute sides, not reading-direction ones, because the system places its controls by side.
 */
@Immutable
data class WorkspaceCaptionBar(
    val height: Dp,
    val left: Dp,
    val right: Dp,
)

/**
 * Overrides the caption bar the workspace reads from the platform, for previews and tests that show
 * the workspace in a desktop window.
 */
val LocalWorkspaceCaptionBar = staticCompositionLocalOf<WorkspaceCaptionBar?> { null }

/**
 * The window's caption bar, or null when it has none (phones, tablets outside desktop windowing,
 * desktop and iOS builds). While this is composed, the platform draws the caption with a transparent
 * background, so the workspace [canvas] reaches the top of the window, and draws its own controls in
 * a color that reads on [canvas].
 */
@Composable
internal expect fun rememberPlatformCaptionBar(canvas: Color): WorkspaceCaptionBar?

/**
 * Counts the screens in each window that want its caption bar transparent. Screens can share a
 * window, such as Home and the editor during navigation, so only the last one to leave hands the
 * caption back to the system.
 */
internal class CaptionRequests<W : Any> {
    private val counts = mutableMapOf<W, Int>()

    fun acquire(window: W) {
        counts[window] = (counts[window] ?: 0) + 1
    }

    /** Returns true when no screen in [window] wants a transparent caption any longer. */
    fun release(window: W): Boolean {
        val remaining = (counts[window] ?: 1) - 1
        if (remaining > 0) {
            counts[window] = remaining
            return false
        }
        counts.remove(window)
        return true
    }
}

/**
 * Lets a screen's own [background] show through the window's caption bar in Android desktop
 * windowing, instead of the system's tinted bar, for screens that keep their controls below the
 * caption, such as the editor. The workspace does the same while it places its app bar inside the
 * caption. Has no effect outside desktop windowing or before Android 15.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun BlendedCaptionBar(background: Color) {
    rememberPlatformCaptionBar(background)
}

/**
 * The widths, in pixels, that the system's caption controls take at the left and right edges of a
 * window [windowWidth] wide, from their bounding rectangles in window coordinates. A rectangle counts
 * toward the edge nearer its center. Each width stays between zero and half the window: while a
 * window is being resized, its rectangles can briefly describe the old size.
 */
internal fun captionControlInsets(
    controls: List<IntRect>,
    windowWidth: Int,
): Pair<Int, Int> {
    if (windowWidth <= 0) return 0 to 0
    val half = windowWidth / 2
    val left = controls.filter { it.center.x < half }.maxOfOrNull { it.right } ?: 0
    val right = controls.filter { it.center.x >= half }.minOfOrNull { windowWidth - it.left } ?: 0
    return left.coerceIn(0, half) to right.coerceIn(0, half)
}

/**
 * Lets a control in the caption bar receive presses. Without it the platform treats a press in the
 * caption bar as the start of dragging the window.
 */
internal expect fun Modifier.captionBarControl(): Modifier
