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
@Preview(name = "Settings overview book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A09_SettingsOverviewBookPosture() {
    BookPostureSettingsScene(SharedScreenshotSceneId.SettingsOverview)
}

@PreviewTest
@Preview(name = "Account settings book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A10_AccountSettingsBookPosture() {
    BookPostureSettingsScene(SharedScreenshotSceneId.AccountSettings)
}

@PreviewTest
@Preview(name = "Privacy settings book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A11_PrivacySettingsBookPosture() {
    BookPostureSettingsScene(SharedScreenshotSceneId.PrivacySettings)
}

@PreviewTest
@Preview(name = "Data settings book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A12_DataSettingsBookPosture() {
    BookPostureSettingsScene(SharedScreenshotSceneId.DataSettings)
}

@PreviewTest
@Preview(name = "Memories settings book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A13_MemoriesSettingsBookPosture() {
    BookPostureSettingsScene(SharedScreenshotSceneId.MemoriesSettings)
}

@PreviewTest
@Preview(name = "Devices settings book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A14_DevicesSettingsBookPosture() {
    BookPostureSettingsScene(SharedScreenshotSceneId.DevicesSettings)
}

@PreviewTest
@Preview(name = "Rewind settings book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A15_RewindSettingsBookPosture() {
    BookPostureSettingsScene(SharedScreenshotSceneId.RewindSettings)
}

@Composable
private fun BookPostureSettingsScene(sceneId: SharedScreenshotSceneId) {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            SharedScreenshotScene(sceneId)
        }
    }
}
