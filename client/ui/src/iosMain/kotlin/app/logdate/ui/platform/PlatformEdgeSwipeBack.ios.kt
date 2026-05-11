package app.logdate.ui.platform

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Leading-edge swipe-back implemented as a Compose pointer gesture. We watch for a first
 * down within [EdgeWidthDp] of the left edge, then accumulate the horizontal drag delta
 * across subsequent events; if the drag clears [ActivationThresholdDp] before the pointer
 * goes up — and is dominantly horizontal — we fire [onBack] once and consume the gesture
 * so child scroll containers don't also react.
 *
 * Bridges to a `UIScreenEdgePanGestureRecognizer` could replace this for higher fidelity
 * later, but the Compose-only path is good enough to avoid the SwiftUI bridge complexity.
 */
private val EdgeWidthDp = 20.dp
private val ActivationThresholdDp = 60.dp

@Composable
actual fun Modifier.iosEdgeSwipeBack(
    enabled: Boolean,
    onBack: () -> Unit,
): Modifier =
    if (!enabled) {
        this
    } else {
        composed {
            val density = LocalDensity.current
            val edgePx = with(density) { EdgeWidthDp.toPx() }
            val activationPx = with(density) { ActivationThresholdDp.toPx() }
            pointerInput(onBack, edgePx, activationPx) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (first.position.x > edgePx) return@awaitEachGesture
                    var dragX = 0f
                    var dragY = 0f
                    var fired = false
                    while (!fired) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        if (event.type == PointerEventType.Release || !change.pressed) break
                        val delta = change.positionChange()
                        dragX += delta.x
                        dragY += delta.y
                        if (dragX > activationPx && dragX > abs(dragY) * 1.2f) {
                            fired = true
                            change.consume()
                            onBack()
                        }
                    }
                }
            }
        }
    }
