package app.logdate.navigation.scenes

import androidx.compose.ui.unit.dp
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeAdaptiveLayoutTest {
    @Test
    fun noHingeUsesExistingExpandedWidthBreakpoint() {
        assertTrue(
            supportsDualPaneHomeScene(
                width = 840.dp,
                height = 900.dp,
                foldableLayoutInfo = FoldableLayoutInfo(),
            ),
        )
    }

    @Test
    fun noHingeKeepsCompactWidthSinglePane() {
        assertFalse(
            supportsDualPaneHomeScene(
                width = 420.dp,
                height = 900.dp,
                foldableLayoutInfo = FoldableLayoutInfo(),
            ),
        )
    }

    @Test
    fun verticalSeparatingHingeEnablesDualPaneWhenBothPanesAreSafe() {
        assertTrue(
            supportsDualPaneHomeScene(
                width = 820.dp,
                height = 720.dp,
                foldableLayoutInfo =
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
            ),
        )
    }

    @Test
    fun verticalSeparatingHingeDisablesDualPaneWhenPaneIsTooSmall() {
        assertFalse(
            supportsDualPaneHomeScene(
                width = 700.dp,
                height = 720.dp,
                foldableLayoutInfo =
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
            ),
        )
    }

    @Test
    fun horizontalSeparatingHingeDoesNotUseSideBySideHomeScene() {
        assertFalse(
            supportsDualPaneHomeScene(
                width = 900.dp,
                height = 720.dp,
                foldableLayoutInfo =
                    foldableLayoutInfo(
                        orientation = FoldableHingeOrientation.Horizontal,
                        posture = FoldablePosture.Tabletop,
                        bounds =
                            FoldableHingeBounds(
                                left = 0.dp,
                                top = 350.dp,
                                right = 900.dp,
                                bottom = 370.dp,
                                width = 900.dp,
                                height = 20.dp,
                            ),
                    ),
            ),
        )
    }

    private fun foldableLayoutInfo(
        orientation: FoldableHingeOrientation,
        posture: FoldablePosture = FoldablePosture.Book,
        bounds: FoldableHingeBounds,
    ): FoldableLayoutInfo =
        FoldableLayoutInfo(
            isFoldable = true,
            posture = posture,
            hinge =
                FoldableHingeInfo(
                    orientation = orientation,
                    state = FoldableHingeState.HalfOpened,
                    occlusionType = FoldableOcclusionType.Full,
                    bounds = bounds,
                    isSeparating = true,
                ),
        )
}
