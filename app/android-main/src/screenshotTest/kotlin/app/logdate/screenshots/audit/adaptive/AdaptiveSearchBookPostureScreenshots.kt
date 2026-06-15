package app.logdate.screenshots.audit.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.screenshots.shared.SharedScreenshotScene
import app.logdate.screenshots.shared.SharedScreenshotSceneId
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import com.android.tools.screenshot.PreviewTest

private const val BOOK_FOLDABLE = "spec:width=1440dp,height=900dp"

private val bookPostureLayoutInfo =
    FoldableLayoutInfo(
        isFoldable = true,
        posture = FoldablePosture.Book,
        hinge =
            FoldableHingeInfo(
                orientation = FoldableHingeOrientation.Vertical,
                state = FoldableHingeState.HalfOpened,
                occlusionType = FoldableOcclusionType.Full,
                bounds =
                    FoldableHingeBounds(
                        left = 708.dp,
                        top = 0.dp,
                        right = 732.dp,
                        bottom = 900.dp,
                        width = 24.dp,
                        height = 900.dp,
                    ),
                isSeparating = true,
            ),
    )

@PreviewTest
@Preview(name = "Search idle book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A30_SearchIdleBookPosture() {
    BookPostureSearchScene(SharedScreenshotSceneId.SearchIdle)
}

@PreviewTest
@Preview(name = "Search searching book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A31_SearchSearchingBookPosture() {
    BookPostureSearchScene(SharedScreenshotSceneId.SearchSearching)
}

@PreviewTest
@Preview(name = "Search empty book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A32_SearchEmptyBookPosture() {
    BookPostureSearchScene(SharedScreenshotSceneId.SearchEmpty)
}

@PreviewTest
@Preview(name = "Search results book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A33_SearchResultsBookPosture() {
    BookPostureSearchScene(SharedScreenshotSceneId.SearchResults)
}

@Composable
private fun BookPostureSearchScene(sceneId: SharedScreenshotSceneId) {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            SharedScreenshotScene(sceneId)
        }
    }
}
