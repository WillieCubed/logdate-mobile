package app.logdate.ui.workspace

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import app.logdate.ui.platform.rememberSystemReduceMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One interruptible motion owner shared by the header and the panel's scrollable content. */
internal class SupportingSheetMotion(
    val state: AnchoredDraggableState<SupportingExtent>,
    private val delegateFling: FlingBehavior,
    private val scope: CoroutineScope,
    private val reduceMotion: Boolean,
) {
    private var animation: Job? = null
    var isSettling by mutableStateOf(false)
        private set

    val flingBehavior =
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                isSettling = true
                return try {
                    with(delegateFling) { performFling(initialVelocity) }
                } finally {
                    isSettling = false
                }
            }
        }

    fun moveTo(extent: SupportingExtent) {
        if (state.targetValue == extent && state.settledValue == extent) return
        animation?.cancel()
        animation = scope.launch { state.animateTo(extent, if (reduceMotion) snap() else tween(240)) }
    }

    private fun drag(delta: Float): Float {
        animation?.cancel()
        return state.dispatchRawDelta(delta)
    }

    private suspend fun settle(velocity: Float): Float {
        animation?.cancel()
        var remaining = velocity
        state.anchoredDrag {
            val scroll =
                object : ScrollScope {
                    override fun scrollBy(pixels: Float): Float = state.dispatchRawDelta(pixels)
                }
            remaining = with(flingBehavior) { scroll.performFling(velocity) }
        }
        return remaining
    }

    val nestedScroll =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset = if (source == NestedScrollSource.UserInput && available.y < 0f) Offset(0f, drag(available.y)) else Offset.Zero

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = if (source == NestedScrollSource.UserInput) Offset(0f, drag(available.y)) else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity =
                if (available.y < 0f && state.requireOffset() > state.anchors.minPosition()) {
                    Velocity(0f, available.y - settle(available.y))
                } else {
                    Velocity.Zero
                }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = Velocity(0f, available.y - settle(available.y))
        }
}

@Composable
internal fun rememberSupportingSheetMotion(
    extent: SupportingExtent,
    anchors: androidx.compose.foundation.gestures.DraggableAnchors<SupportingExtent>,
    onSettled: (SupportingExtent) -> Unit,
): SupportingSheetMotion {
    val fallback = if (anchors.positionOf(SupportingExtent.Focused).isNaN()) SupportingExtent.Peek else SupportingExtent.Focused
    val target = if (anchors.positionOf(extent).isNaN()) fallback else extent
    val state = remember { AnchoredDraggableState(target, anchors) }
    // Anchor updates preserve the current destination, including when available height changes for IME/resize.
    SideEffect {
        state.updateAnchors(anchors, if (anchors.positionOf(state.targetValue).isNaN()) fallback else state.targetValue)
    }
    val reduceMotion by rememberSystemReduceMotion()
    val fling = AnchoredDraggableDefaults.flingBehavior(state, animationSpec = if (reduceMotion) snap() else tween(240))
    val scope = rememberCoroutineScope()
    val motion = remember(state, fling, scope, reduceMotion) { SupportingSheetMotion(state, fling, scope, reduceMotion) }
    val settled = rememberUpdatedState(onSettled)
    LaunchedEffect(state) { snapshotFlow { state.settledValue }.collect { settled.value(it) } }
    LaunchedEffect(target, extent, motion) {
        if (target != extent) settled.value(target) else motion.moveTo(target)
    }
    return motion
}

internal fun SupportingSheetAnchors.dragAnchors(
    height: Float,
    density: Float,
) = DraggableAnchors {
    val collapsed = if (canOverlay) peek.value * density else minOf(64f * density, height)
    SupportingExtent.Peek at -collapsed
    if (canOverlay) {
        SupportingExtent.Browsing at -browsing.value * density
        SupportingExtent.Expanded at -expanded.value * density
    }
    if (height > collapsed) SupportingExtent.Focused at -height
}
