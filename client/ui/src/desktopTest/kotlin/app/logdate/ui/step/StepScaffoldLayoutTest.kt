@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.step

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import app.logdate.ui.theme.LogDateTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ROOT_TAG = "step_root"
private const val HERO_TAG = "step_hero"
private const val LAST_CONTENT_TAG = "step_last_content"
private const val PRIMARY_ACTION_TAG = "step_primary_action"
private const val TITLE = "Choose your handle"

/**
 * Geometry guarantees [StepScaffold] makes in every posture. Each onboarding step once hand-rolled
 * these, and each got at least one of them wrong: content scrolling underneath the buttons,
 * buttons stretched across a whole foldable pane, and short steps pinned to the top of the screen
 * above a large empty gap.
 */
@OptIn(ExperimentalTestApi::class)
class StepScaffoldLayoutTest {
    @Test
    fun `scrolled-to-end content stays above the actions on a phone`() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            setStep(contentItems = 30)

            onNodeWithTag(LAST_CONTENT_TAG).performScrollTo()

            val last = bounds(LAST_CONTENT_TAG)
            val action = bounds(PRIMARY_ACTION_TAG)
            assertTrue(last.bottom <= action.top, "last content (bottom ${last.bottom}) is under the actions (top ${action.top})")
        }

    @Test
    fun `short content is centered between the top and the actions on a phone`() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            setStep(contentItems = 1, onBack = null)

            val title = onNodeWithText(TITLE).getBoundsInRoot()
            val last = bounds(LAST_CONTENT_TAG)
            val action = bounds(PRIMARY_ACTION_TAG)
            val above = title.top
            val below = action.top - last.bottom
            assertTrue(above > 120.dp, "short content hugs the top (title at $above)")
            assertTrue(abs((above - below).value) <= 48f, "content is not centered: $above above, $below below")
        }

    @Test
    fun `hero sits above the title`() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            setStep(contentItems = 1, withHero = true)

            val hero = bounds(HERO_TAG)
            val title = onNodeWithText(TITLE).getBoundsInRoot()
            assertTrue(hero.bottom <= title.top, "hero (bottom ${hero.bottom}) overlaps title (top ${title.top})")
        }

    @Test
    fun `wide unfolded window places actions beside the body`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setStep(contentItems = 1)

            val title = onNodeWithText(TITLE).getBoundsInRoot()
            val action = bounds(PRIMARY_ACTION_TAG)
            assertTrue(action.left >= title.right, "actions (left ${action.left}) are not beside the body (right ${title.right})")
            assertCapped(action)
        }

    @Test
    fun `landscape phone places actions beside the body`() =
        runDesktopComposeUiTest(width = 891, height = 411) {
            setStep(contentItems = 1)

            val title = onNodeWithText(TITLE).getBoundsInRoot()
            val action = bounds(PRIMARY_ACTION_TAG)
            assertTrue(action.left >= title.right, "actions (left ${action.left}) are not beside the body (right ${title.right})")
        }

    @Test
    fun `book posture keeps body and actions on opposite sides of the hinge`() =
        runDesktopComposeUiTest(width = 1440, height = 900) {
            setStep(contentItems = 1, foldable = bookPosture)

            val title = onNodeWithText(TITLE).getBoundsInRoot()
            val action = bounds(PRIMARY_ACTION_TAG)
            assertTrue(title.right <= 708.dp, "title crosses the hinge (right ${title.right})")
            assertTrue(action.left >= 732.dp, "actions cross the hinge (left ${action.left})")
            assertCapped(action)
        }

    @Test
    fun `tabletop posture keeps body above and actions below the hinge`() =
        runDesktopComposeUiTest(width = 1440, height = 900) {
            setStep(contentItems = 1, foldable = tabletopPosture)

            val title = onNodeWithText(TITLE).getBoundsInRoot()
            val action = bounds(PRIMARY_ACTION_TAG)
            assertTrue(title.bottom <= 438.dp, "title crosses the hinge (bottom ${title.bottom})")
            assertTrue(action.top >= 462.dp, "actions cross the hinge (top ${action.top})")
            assertCapped(action)
        }

    @Test
    fun `tabletop posture puts content below the hinge, not cut off above it`() =
        runDesktopComposeUiTest(width = 1440, height = 900) {
            setStep(contentItems = 3, foldable = tabletopPosture)

            val first = bounds("step_content_0")
            val last = bounds(LAST_CONTENT_TAG)
            assertTrue(first.top >= 462.dp, "content (top ${first.top}) sits above the hinge")
            assertClear(last, bounds(PRIMARY_ACTION_TAG))
        }

    @Test
    fun `overflowing tabletop content scrolls between the hinge and the actions`() =
        runDesktopComposeUiTest(width = 1440, height = 900) {
            setStep(contentItems = 30, foldable = tabletopPosture)

            onNodeWithTag(LAST_CONTENT_TAG).performScrollTo()

            val last = bounds(LAST_CONTENT_TAG)
            assertTrue(last.top >= 462.dp, "content (top ${last.top}) scrolled across the hinge")
            assertTrue(last.bottom <= 900.dp, "content (bottom ${last.bottom}) runs off the screen")
            assertClear(last, bounds(PRIMARY_ACTION_TAG))
        }

    @Test
    fun `a wide tabletop pane puts content beside the actions instead of scrolling it`() =
        runDesktopComposeUiTest(width = 1440, height = 900) {
            setStep(contentItems = 6, foldable = tabletopPosture)

            val last = bounds(LAST_CONTENT_TAG)
            val action = bounds(PRIMARY_ACTION_TAG)
            assertTrue(action.left >= last.right, "actions (left ${action.left}) are not beside the content (right ${last.right})")
            assertTrue(last.bottom <= 900.dp, "content (bottom ${last.bottom}) needs scrolling in a two-column pane")
        }

    @Test
    fun `progress stays within the content column on a wide window`() =
        runDesktopComposeUiTest(width = 1440, height = 900) {
            setContent { LogDateTheme { SampleStep(contentItems = 1, onBack = {}, withHero = false, progress = StepProgress(1, 2)) } }

            val bar = onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.5f, 0f..1f))).getBoundsInRoot()
            assertTrue(bar.right - bar.left <= StepScaffoldDefaults.ContentMaxWidth, "progress spans ${bar.right - bar.left}")
        }

    @Test
    fun `the step is opaque so transitions never show another step through it`() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            // No LogDateTheme here: its root Surface would paint an opaque background of its own.
            setContent { MaterialTheme { SampleStep(contentItems = 1, onBack = {}, withHero = false) } }

            val pixels = onNodeWithTag(ROOT_TAG).captureToImage().toPixelMap()
            val corner = pixels[pixels.width - 1, pixels.height / 2]
            assertEquals(1f, corner.alpha, "step background is not opaque")
        }

    @Test
    fun `every posture carries exactly one root tag`() {
        listOf(null, bookPosture, tabletopPosture).forEach { posture ->
            runDesktopComposeUiTest(width = 1440, height = 900) {
                setStep(contentItems = 1, foldable = posture)
                onAllNodesWithTag(ROOT_TAG).assertCountEquals(1)
            }
        }
    }

    private fun DesktopComposeUiTest.bounds(tag: String): DpRect = onNodeWithTag(tag).getBoundsInRoot()

    private fun assertClear(
        content: DpRect,
        action: DpRect,
    ) {
        val overlaps =
            content.left < action.right &&
                action.left < content.right &&
                content.top < action.bottom &&
                action.top < content.bottom
        assertTrue(!overlaps, "content $content overlaps the actions $action")
    }

    private fun assertCapped(action: DpRect) {
        val width = action.right - action.left
        assertTrue(width <= StepScaffoldDefaults.ContentMaxWidth, "action is $width wide, past the content cap")
    }

    private fun DesktopComposeUiTest.setStep(
        contentItems: Int,
        onBack: (() -> Unit)? = {},
        withHero: Boolean = false,
        foldable: FoldableLayoutInfo? = null,
    ) {
        setContent {
            LogDateTheme {
                if (foldable != null) {
                    provideFoldableLayoutInfo(foldable) { SampleStep(contentItems, onBack, withHero) }
                } else {
                    SampleStep(contentItems, onBack, withHero)
                }
            }
        }
    }
}

@Composable
private fun SampleStep(
    contentItems: Int,
    onBack: (() -> Unit)?,
    withHero: Boolean,
    progress: StepProgress? = null,
) {
    StepScaffold(
        progress = progress,
        title = TITLE,
        onBack = onBack,
        modifier = Modifier.testTag(ROOT_TAG),
        supportingText = "This is how other people find you. You can change it later.",
        hero =
            if (withHero) {
                { StepHeroIcon(icon = Icons.Rounded.Star, modifier = Modifier.testTag(HERO_TAG)) }
            } else {
                null
            },
        actions = {
            Button(onClick = {}, modifier = Modifier.fillMaxWidth().testTag(PRIMARY_ACTION_TAG)) {
                Text("Continue")
            }
            TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Not now")
            }
        },
    ) {
        repeat(contentItems) { index ->
            val tag = if (index == contentItems - 1) LAST_CONTENT_TAG else "step_content_$index"
            Box(modifier = Modifier.fillMaxWidth().height(40.dp).testTag(tag))
        }
    }
}

private val bookPosture =
    FoldableLayoutInfo(
        isFoldable = true,
        posture = FoldablePosture.Book,
        hinge =
            FoldableHingeInfo(
                orientation = FoldableHingeOrientation.Vertical,
                state = FoldableHingeState.HalfOpened,
                occlusionType = FoldableOcclusionType.Full,
                bounds = FoldableHingeBounds(left = 708.dp, top = 0.dp, right = 732.dp, bottom = 900.dp, width = 24.dp, height = 900.dp),
                isSeparating = true,
            ),
    )

private val tabletopPosture =
    FoldableLayoutInfo(
        isFoldable = true,
        posture = FoldablePosture.Tabletop,
        hinge =
            FoldableHingeInfo(
                orientation = FoldableHingeOrientation.Horizontal,
                state = FoldableHingeState.HalfOpened,
                occlusionType = FoldableOcclusionType.Full,
                bounds = FoldableHingeBounds(left = 0.dp, top = 438.dp, right = 1440.dp, bottom = 462.dp, width = 1440.dp, height = 24.dp),
                isSeparating = true,
            ),
    )
