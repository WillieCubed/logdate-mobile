package app.logdate.screenshots.components.step

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTestData.TABLET
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import app.logdate.ui.step.StepProgress
import app.logdate.ui.step.StepScaffold
import com.android.tools.screenshot.PreviewTest

/**
 * Regression guard for [StepScaffold].
 *
 * Cloud account creation and sign-in were both unreachable for a period because each screen
 * hand-rolled its own adaptive layout and the compact branch silently dropped the buttons. The
 * baselines were regenerated afterwards and accepted, so nothing flagged it.
 *
 * These previews render the same scaffold in all three postures. If any posture ever stops drawing
 * its actions, the corresponding baseline changes and review catches it.
 */
@Composable
private fun SampleStep() {
    StepScaffold(
        title = "Choose your handle",
        onBack = {},
        supportingText = "This is how other people find you. You can change it later.",
        progress = StepProgress(current = 2, total = 4),
        actions = {
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
            TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Not now")
            }
        },
    )
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun S01_StepScaffoldCompact() {
    ScreenshotTheme {
        SampleStep()
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun S02_StepScaffoldCompactDark() {
    ScreenshotTheme(darkTheme = true) {
        SampleStep()
    }
}

@PreviewTest
@Preview(showBackground = true, device = TABLET)
@Composable
fun S03_StepScaffoldBookPosture() {
    ScreenshotTheme {
        provideFoldableLayoutInfo(bookPostureLayoutInfo) {
            SampleStep()
        }
    }
}

@PreviewTest
@Preview(showBackground = true, device = TABLET)
@Composable
fun S04_StepScaffoldTabletopPosture() {
    ScreenshotTheme {
        provideFoldableLayoutInfo(tabletopPostureLayoutInfo) {
            SampleStep()
        }
    }
}

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
                        left = 628.dp,
                        top = 0.dp,
                        right = 652.dp,
                        bottom = 800.dp,
                        width = 24.dp,
                        height = 800.dp,
                    ),
                isSeparating = true,
            ),
    )

private val tabletopPostureLayoutInfo =
    FoldableLayoutInfo(
        isFoldable = true,
        posture = FoldablePosture.Tabletop,
        hinge =
            FoldableHingeInfo(
                orientation = FoldableHingeOrientation.Horizontal,
                state = FoldableHingeState.HalfOpened,
                occlusionType = FoldableOcclusionType.Full,
                bounds =
                    FoldableHingeBounds(
                        left = 0.dp,
                        top = 388.dp,
                        right = 1280.dp,
                        bottom = 412.dp,
                        width = 1280.dp,
                        height = 24.dp,
                    ),
                isSeparating = true,
            ),
    )
