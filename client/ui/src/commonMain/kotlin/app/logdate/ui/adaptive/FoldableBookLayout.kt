@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableSplitLayout
import app.logdate.ui.foldable.calculateFoldableSplitLayout
import app.logdate.ui.foldable.rememberFoldableLayoutInfo

@Immutable
data class FoldableBookPaneInfo(
    val width: Dp,
    val height: Dp,
)

@Composable
fun FoldableBookLayout(
    modifier: Modifier = Modifier,
    foldableLayoutInfo: FoldableLayoutInfo = rememberFoldableLayoutInfo(),
    minPaneWidth: Dp = 320.dp,
    startPane: @Composable BoxScope.(FoldableBookPaneInfo) -> Unit,
    endPane: @Composable BoxScope.(FoldableBookPaneInfo) -> Unit,
    singlePaneContent: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val splitLayout =
            calculateFoldableSplitLayout(
                containerWidth = maxWidth,
                containerHeight = maxHeight,
                layoutInfo = foldableLayoutInfo,
                minPaneWidth = minPaneWidth,
            )

        if (splitLayout is FoldableSplitLayout.Vertical) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier =
                        Modifier
                            .width(splitLayout.leftPane.width)
                            .fillMaxHeight(),
                ) {
                    startPane(
                        FoldableBookPaneInfo(
                            width = splitLayout.leftPane.width,
                            height = splitLayout.leftPane.height,
                        ),
                    )
                }
                Spacer(
                    modifier =
                        Modifier
                            .width(splitLayout.hingeBounds.width)
                            .fillMaxHeight(),
                )
                Box(
                    modifier =
                        Modifier
                            .width(splitLayout.rightPane.width)
                            .fillMaxHeight(),
                ) {
                    endPane(
                        FoldableBookPaneInfo(
                            width = splitLayout.rightPane.width,
                            height = splitLayout.rightPane.height,
                        ),
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                singlePaneContent()
            }
        }
    }
}
