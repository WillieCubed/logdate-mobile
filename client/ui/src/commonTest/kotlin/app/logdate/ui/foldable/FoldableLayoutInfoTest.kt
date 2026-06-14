package app.logdate.ui.foldable

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FoldableLayoutInfoTest {
    @Test
    fun splitLayoutReturnsNoneWhenDeviceHasNoHinge() {
        val split =
            calculateFoldableSplitLayout(
                containerWidth = 840.dp,
                containerHeight = 720.dp,
                layoutInfo = FoldableLayoutInfo(),
            )

        assertEquals(FoldableSplitLayout.None, split)
    }

    @Test
    fun verticalSeparatingHingeProducesLeftAndRightPaneBounds() {
        val split =
            calculateFoldableSplitLayout(
                containerWidth = 820.dp,
                containerHeight = 720.dp,
                layoutInfo =
                    foldableLayoutInfo(
                        orientation = FoldableHingeOrientation.Vertical,
                        bounds =
                            FoldableHingeBounds(
                                left = 400.dp,
                                top = 0.dp,
                                right = 420.dp,
                                bottom = 720.dp,
                                width = 20.dp,
                                height = 720.dp,
                            ),
                    ),
            )
        val vertical = assertIs<FoldableSplitLayout.Vertical>(split)

        assertEquals(400.dp, vertical.leftPane.width)
        assertEquals(400.dp, vertical.rightPane.width)
        assertEquals(20.dp, vertical.hingeBounds.width)
    }

    @Test
    fun horizontalSeparatingHingeProducesTopAndBottomPaneBounds() {
        val split =
            calculateFoldableSplitLayout(
                containerWidth = 820.dp,
                containerHeight = 720.dp,
                layoutInfo =
                    foldableLayoutInfo(
                        orientation = FoldableHingeOrientation.Horizontal,
                        bounds =
                            FoldableHingeBounds(
                                left = 0.dp,
                                top = 350.dp,
                                right = 820.dp,
                                bottom = 370.dp,
                                width = 820.dp,
                                height = 20.dp,
                            ),
                    ),
            )
        val horizontal = assertIs<FoldableSplitLayout.Horizontal>(split)

        assertEquals(350.dp, horizontal.topPane.height)
        assertEquals(350.dp, horizontal.bottomPane.height)
        assertEquals(20.dp, horizontal.hingeBounds.height)
    }

    @Test
    fun splitLayoutReturnsNoneWhenSafePaneIsTooSmall() {
        val split =
            calculateFoldableSplitLayout(
                containerWidth = 700.dp,
                containerHeight = 720.dp,
                layoutInfo =
                    foldableLayoutInfo(
                        orientation = FoldableHingeOrientation.Vertical,
                        bounds =
                            FoldableHingeBounds(
                                left = 280.dp,
                                top = 0.dp,
                                right = 300.dp,
                                bottom = 720.dp,
                                width = 20.dp,
                                height = 720.dp,
                            ),
                    ),
            )

        assertEquals(FoldableSplitLayout.None, split)
    }

    @Test
    fun splitLayoutReturnsNoneWhenHingeDoesNotSeparateWindow() {
        val split =
            calculateFoldableSplitLayout(
                containerWidth = 820.dp,
                containerHeight = 720.dp,
                layoutInfo = foldableLayoutInfo(isSeparating = false),
            )

        assertEquals(FoldableSplitLayout.None, split)
    }

    @Test
    fun pixelsToDpUsesDensityScale() {
        assertEquals(120f, pixelsToDp(px = 360, density = 3f))
    }

    private fun foldableLayoutInfo(
        orientation: FoldableHingeOrientation = FoldableHingeOrientation.Vertical,
        bounds: FoldableHingeBounds =
            FoldableHingeBounds(
                left = 400.dp,
                top = 0.dp,
                right = 420.dp,
                bottom = 720.dp,
                width = 20.dp,
                height = 720.dp,
            ),
        isSeparating: Boolean = true,
    ): FoldableLayoutInfo =
        FoldableLayoutInfo(
            isFoldable = true,
            posture = FoldablePosture.Book,
            hinge =
                FoldableHingeInfo(
                    orientation = orientation,
                    state = FoldableHingeState.HalfOpened,
                    occlusionType = FoldableOcclusionType.Full,
                    bounds = bounds,
                    isSeparating = isSeparating,
                ),
        )
}
