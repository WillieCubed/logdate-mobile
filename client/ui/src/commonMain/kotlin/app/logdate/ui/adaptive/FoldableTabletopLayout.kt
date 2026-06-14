@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
data class FoldableTabletopPaneInfo(
    val width: Dp,
    val height: Dp,
)

@Composable
fun FoldableTabletopLayout(
    modifier: Modifier = Modifier,
    foldableLayoutInfo: FoldableLayoutInfo = rememberFoldableLayoutInfo(),
    minPaneHeight: Dp = 220.dp,
    topPane: @Composable BoxScope.(FoldableTabletopPaneInfo) -> Unit,
    bottomPane: @Composable BoxScope.(FoldableTabletopPaneInfo) -> Unit,
    fallback: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val splitLayout =
            calculateFoldableSplitLayout(
                containerWidth = maxWidth,
                containerHeight = maxHeight,
                layoutInfo = foldableLayoutInfo,
                minPaneHeight = minPaneHeight,
            )

        if (splitLayout is FoldableSplitLayout.Horizontal) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(splitLayout.topPane.height),
                ) {
                    topPane(
                        FoldableTabletopPaneInfo(
                            width = splitLayout.topPane.width,
                            height = splitLayout.topPane.height,
                        ),
                    )
                }
                Spacer(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(splitLayout.hingeBounds.height),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(splitLayout.bottomPane.height),
                ) {
                    bottomPane(
                        FoldableTabletopPaneInfo(
                            width = splitLayout.bottomPane.width,
                            height = splitLayout.bottomPane.height,
                        ),
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                fallback()
            }
        }
    }
}
