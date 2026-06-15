@file:Suppress("ktlint:standard:filename")

package app.logdate.ui.foldable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Basic foldable device information available across all platforms.
 *
 * On platforms without foldable support (iOS, Desktop), this will always
 * return non-foldable state.
 */
data class FoldableState(
    val isFoldable: Boolean = false,
    val isHalfOpened: Boolean = false,
    val hasVerticalHinge: Boolean = false,
    val hasHorizontalHinge: Boolean = false,
)

data class FoldableLayoutInfo(
    val isFoldable: Boolean = false,
    val posture: FoldablePosture = FoldablePosture.Standard,
    val hinge: FoldableHingeInfo? = null,
)

enum class FoldablePosture {
    Standard,
    Book,
    Tabletop,
}

data class FoldableHingeInfo(
    val orientation: FoldableHingeOrientation,
    val state: FoldableHingeState,
    val occlusionType: FoldableOcclusionType,
    val bounds: FoldableHingeBounds,
    val isSeparating: Boolean,
)

enum class FoldableHingeOrientation {
    Vertical,
    Horizontal,
    Unknown,
}

enum class FoldableHingeState {
    Flat,
    HalfOpened,
    Unknown,
}

enum class FoldableOcclusionType {
    None,
    Full,
    Unknown,
}

data class FoldableHingeBounds(
    val left: Dp,
    val top: Dp,
    val right: Dp,
    val bottom: Dp,
    val width: Dp,
    val height: Dp,
)

data class FoldablePaneBounds(
    val left: Dp,
    val top: Dp,
    val right: Dp,
    val bottom: Dp,
    val width: Dp,
    val height: Dp,
)

sealed interface FoldableSplitLayout {
    data object None : FoldableSplitLayout

    data class Vertical(
        val leftPane: FoldablePaneBounds,
        val rightPane: FoldablePaneBounds,
        val hingeBounds: FoldableHingeBounds,
    ) : FoldableSplitLayout

    data class Horizontal(
        val topPane: FoldablePaneBounds,
        val bottomPane: FoldablePaneBounds,
        val hingeBounds: FoldableHingeBounds,
    ) : FoldableSplitLayout
}

/**
 * Remembers the current foldable state of the device.
 *
 * This is a cross-platform composable that provides foldable device
 * information. On Android, it uses androidx.window to detect foldables.
 * On other platforms, it returns a non-foldable state.
 *
 * @return Current foldable state
 */
@Composable
fun rememberFoldableState(): FoldableState = rememberFoldableLayoutInfo().toFoldableState()

@Composable
expect fun rememberFoldableLayoutInfo(): FoldableLayoutInfo

val LocalFoldableLayoutInfoOverride =
    staticCompositionLocalOf<FoldableLayoutInfo?> { null }

@Composable
fun provideFoldableLayoutInfo(
    foldableLayoutInfo: FoldableLayoutInfo,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFoldableLayoutInfoOverride provides foldableLayoutInfo) {
        content()
    }
}

fun calculateFoldableSplitLayout(
    containerWidth: Dp,
    containerHeight: Dp,
    layoutInfo: FoldableLayoutInfo,
    minPaneWidth: Dp = 320.dp,
    minPaneHeight: Dp = 280.dp,
): FoldableSplitLayout {
    val hinge = layoutInfo.hinge ?: return FoldableSplitLayout.None
    if (!hinge.isSeparating) return FoldableSplitLayout.None

    return when (hinge.orientation) {
        FoldableHingeOrientation.Vertical -> {
            val leftWidth = hinge.bounds.left.coerceAtLeast(0.dp)
            val rightWidth = (containerWidth - hinge.bounds.right).coerceAtLeast(0.dp)
            if (leftWidth < minPaneWidth || rightWidth < minPaneWidth) {
                FoldableSplitLayout.None
            } else {
                FoldableSplitLayout.Vertical(
                    leftPane =
                        FoldablePaneBounds(
                            left = 0.dp,
                            top = 0.dp,
                            right = hinge.bounds.left,
                            bottom = containerHeight,
                            width = leftWidth,
                            height = containerHeight,
                        ),
                    rightPane =
                        FoldablePaneBounds(
                            left = hinge.bounds.right,
                            top = 0.dp,
                            right = containerWidth,
                            bottom = containerHeight,
                            width = rightWidth,
                            height = containerHeight,
                        ),
                    hingeBounds = hinge.bounds,
                )
            }
        }
        FoldableHingeOrientation.Horizontal -> {
            val topHeight = hinge.bounds.top.coerceAtLeast(0.dp)
            val bottomHeight = (containerHeight - hinge.bounds.bottom).coerceAtLeast(0.dp)
            if (topHeight < minPaneHeight || bottomHeight < minPaneHeight) {
                FoldableSplitLayout.None
            } else {
                FoldableSplitLayout.Horizontal(
                    topPane =
                        FoldablePaneBounds(
                            left = 0.dp,
                            top = 0.dp,
                            right = containerWidth,
                            bottom = hinge.bounds.top,
                            width = containerWidth,
                            height = topHeight,
                        ),
                    bottomPane =
                        FoldablePaneBounds(
                            left = 0.dp,
                            top = hinge.bounds.bottom,
                            right = containerWidth,
                            bottom = containerHeight,
                            width = containerWidth,
                            height = bottomHeight,
                        ),
                    hingeBounds = hinge.bounds,
                )
            }
        }
        FoldableHingeOrientation.Unknown -> FoldableSplitLayout.None
    }
}

internal fun pixelsToDp(
    px: Int,
    density: Float,
): Float = px / density

private fun FoldableLayoutInfo.toFoldableState(): FoldableState {
    val hinge = hinge
    return FoldableState(
        isFoldable = isFoldable,
        isHalfOpened = hinge?.state == FoldableHingeState.HalfOpened,
        hasVerticalHinge = hinge?.orientation == FoldableHingeOrientation.Vertical,
        hasHorizontalHinge = hinge?.orientation == FoldableHingeOrientation.Horizontal,
    )
}
