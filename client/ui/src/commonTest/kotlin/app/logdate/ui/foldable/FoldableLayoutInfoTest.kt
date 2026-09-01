package app.logdate.ui.foldable

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FoldableLayoutInfoTest {
    @Test
    fun `split layout returns none when device has no hinge`() {
        val split =
            calculateFoldableSplitLayout(
                containerWidth = 840.dp,
                containerHeight = 720.dp,
                layoutInfo = FoldableLayoutInfo(),
            )

        assertEquals(FoldableSplitLayout.None, split)
    }

    @Test
    fun `vertical separating hinge produces left and right pane bounds`() {
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
    fun `horizontal separating hinge produces top and bottom pane bounds`() {
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
    fun `split layout returns none when safe pane is too small`() {
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
    fun `split layout returns none when hinge does not separate window`() {
        val split =
            calculateFoldableSplitLayout(
                containerWidth = 820.dp,
                containerHeight = 720.dp,
                layoutInfo = foldableLayoutInfo(isSeparating = false),
            )

        assertEquals(FoldableSplitLayout.None, split)
    }

    @Test
    fun `pixels to dp uses density scale`() {
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
