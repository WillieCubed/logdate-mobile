package app.logdate.feature.editor.ui.layout

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * A class that detects overscroll at the bottom of a scrollable container.
 * This is used to implement the expandable toolbar that appears when the user scrolls past the end.
 */
class OverscrollDetector(
    private val onOverscroll: (Float) -> Unit,
    private val onOverscrollReleased: (Float, Float) -> Unit,
    private val overscrollThreshold: Float = 48f,
    private val maxOverscroll: Float = 120f,
) {
    // Current overscroll amount (0 means no overscroll)
    var overscrollAmount by mutableFloatStateOf(0f)
        private set

    // Whether the overscroll has passed the threshold to trigger expansion
    val isPastThreshold: Boolean
        get() = overscrollAmount >= overscrollThreshold

    // Progress from 0.0 to 1.0 for animation purposes
    val progressFraction: Float
        get() = (overscrollAmount / maxOverscroll).coerceIn(0f, 1f)

    // Nested scroll connection to intercept scroll events
    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Only interested in downward scroll (positive y) for bottom overscroll
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            // If there's unconsumed downward scroll and we're at the bottom of the list
            if (available.y > 0) {
                val newOverscroll = (overscrollAmount + available.y).coerceIn(0f, maxOverscroll)
                if (newOverscroll != overscrollAmount) {
                    overscrollAmount = newOverscroll
                    onOverscroll(overscrollAmount)
                }
                // Consume the available scroll
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // Release overscroll when flinging
            if (overscrollAmount > 0) {
                onOverscrollReleased(overscrollAmount, overscrollThreshold)
                // Reset after notifying
                overscrollAmount = 0f
            }
            return Velocity.Zero
        }

        // Handle when the user stops dragging
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (overscrollAmount > 0) {
                onOverscrollReleased(overscrollAmount, overscrollThreshold)
                // Don't reset yet as we want the animation to run
            }
            return Velocity.Zero
        }
    }

    // Reset the overscroll amount
    fun reset() {
        overscrollAmount = 0f
    }
}

typealias OverscrollReleaseCallback = (amount: Float, overscrollThreshold: Float) -> Unit

/**
 * Creates and remembers an [OverscrollDetector] for the given scroll state.
 */
@Composable
fun rememberOverscrollDetector(
    scrollState: ScrollableState,
    onOverscrollReleased: OverscrollReleaseCallback = { _, _ -> },
    overscrollThreshold: Float = 48f,
    maxOverscroll: Float = 120f,
): OverscrollDetector {
    // TODO: Determine whether this implementation is accurate
    // Check if we're at the bottom of the scrollable content
    val isAtBottom by remember(scrollState) {
        derivedStateOf {
            !scrollState.canScrollForward
//            scrollState. >= (scrollState.maxValue - 2)
        }
    }

    return remember(scrollState, onOverscrollReleased) {
        OverscrollDetector(
            onOverscroll = { amount ->
                // Only process overscroll when at the bottom
                if (isAtBottom) {
                    // Just allow the amount to be tracked - no additional processing needed
                    // The UI will read the amount directly from the detector
                }
            },
            onOverscrollReleased = { amount, _ ->
                // Only handle release when at the bottom
                if (isAtBottom) {
                    onOverscrollReleased(amount, overscrollThreshold)
                }
            },
            overscrollThreshold = overscrollThreshold,
            maxOverscroll = maxOverscroll
        )
    }
}

/**
 * Modifier extension to apply the overscroll detector.
 */
fun Modifier.overscrollDetector(detector: OverscrollDetector): Modifier {
    return this.nestedScroll(detector.nestedScrollConnection)
}