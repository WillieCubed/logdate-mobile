@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Hinge bounds are in window coordinates. A foldable layout that isn't at the window origin -- under
 * a top app bar, beside a navigation rail -- must still split exactly at the hinge.
 */
@OptIn(ExperimentalTestApi::class)
class FoldableLayoutPlacementTest {
    @Test
    fun `tabletop split lands on the hinge when the layout sits under a top bar`() =
        runDesktopComposeUiTest(width = 1440, height = 900) {
            setContent {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.height(64.dp))
                    FoldableTabletopLayout(
                        foldableLayoutInfo = tabletop,
                        topPane = { Box(modifier = Modifier.fillMaxSize().testTag("top")) },
                        bottomPane = { Box(modifier = Modifier.fillMaxSize().testTag("bottom")) },
                        standardContent = {},
                    )
                }
            }

            assertEquals(438.dp, onNodeWithTag("top").getBoundsInRoot().bottom)
            assertEquals(462.dp, onNodeWithTag("bottom").getBoundsInRoot().top)
        }

    @Test
    fun `book split lands on the hinge when the layout sits beside a rail`() =
        runDesktopComposeUiTest(width = 1440, height = 900) {
            setContent {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.width(80.dp))
                    FoldableBookLayout(
                        foldableLayoutInfo = book,
                        startPane = { Box(modifier = Modifier.fillMaxSize().testTag("start")) },
                        endPane = { Box(modifier = Modifier.fillMaxSize().testTag("end")) },
                        standardContent = {},
                    )
                }
            }

            assertEquals(708.dp, onNodeWithTag("start").getBoundsInRoot().right)
            assertEquals(732.dp, onNodeWithTag("end").getBoundsInRoot().left)
        }
}

private val tabletop =
    FoldableLayoutInfo(
        isFoldable = true,
        posture = FoldablePosture.Tabletop,
        hinge =
            FoldableHingeInfo(
                orientation = FoldableHingeOrientation.Horizontal,
                state = FoldableHingeState.HalfOpened,
                occlusionType = FoldableOcclusionType.Full,
                bounds = FoldableHingeBounds(0.dp, 438.dp, 1440.dp, 462.dp, 1440.dp, 24.dp),
                isSeparating = true,
            ),
    )

private val book =
    FoldableLayoutInfo(
        isFoldable = true,
        posture = FoldablePosture.Book,
        hinge =
            FoldableHingeInfo(
                orientation = FoldableHingeOrientation.Vertical,
                state = FoldableHingeState.HalfOpened,
                occlusionType = FoldableOcclusionType.Full,
                bounds = FoldableHingeBounds(708.dp, 0.dp, 732.dp, 900.dp, 24.dp, 900.dp),
                isSeparating = true,
            ),
    )
