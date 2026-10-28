package app.logdate.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.ui.platform.LocalReduceMotionOverride
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorkspaceSheetGestureTest {
    @Test fun headerFollowsTheFingerBeforeReleaseAndReversesWithoutJumping() =
        runDesktopComposeUiTest(width = 411, height = 800) {
            setContent {
                LogDateTheme {
                    WorkspaceSupportingSheet(
                        "Summary",
                        initialExtent = SupportingExtent.Browsing,
                        header = { Text("Browse places") },
                        focus = {},
                        supporting = { Text("Selected place") },
                    )
                }
            }
            val header = onNodeWithText("Browse places")
            val start = header.fetchSemanticsNode().boundsInRoot.top
            header.performTouchInput {
                down(center)
                moveBy(Offset(0f, -100f), 300)
            }
            val raised = header.fetchSemanticsNode().boundsInRoot.top
            assertTrue(raised < start - 60f, "Sheet must follow an active finger, before release: $start -> $raised")
            header.performTouchInput { moveBy(Offset(0f, 50f), 200) }
            val reversed = header.fetchSemanticsNode().boundsInRoot.top
            assertEquals(50f, reversed - raised, 2f, "Reversal must continue from the current position")
            header.performTouchInput { up() }
            assertEquals(start, header.fetchSemanticsNode().boundsInRoot.top, 1f, "Release settles to the nearest anchor")
            onNodeWithText("Selected place").fetchSemanticsNode()
        }

    @Test fun contentDragExpandsBeforeScrollingAndCollapsesAtItsTopBoundary() =
        runDesktopComposeUiTest(width = 411, height = 800) {
            setContent {
                LogDateTheme {
                    WorkspaceSupportingSheet(
                        "Summary",
                        initialExtent = SupportingExtent.Browsing,
                        header = { Text("Browse places") },
                        focus = {},
                        supporting = {
                            LazyColumn(Modifier.fillMaxSize().testTag("places_list")) {
                                items(30) { Text("Place $it", Modifier.height(64.dp)) }
                            }
                        },
                    )
                }
            }
            val header = onNodeWithText("Browse places")
            val list = onNodeWithTag("places_list")
            val start = header.fetchSemanticsNode().boundsInRoot.top
            list.performTouchInput {
                down(Offset(center.x, 80f))
                moveBy(Offset(0f, -100f), 300)
            }
            val raised = header.fetchSemanticsNode().boundsInRoot.top
            assertTrue(raised < start - 60f, "Content swipe must expand the sheet before scrolling its rows")
            list.performTouchInput { moveBy(Offset(0f, 70f), 300) }
            assertTrue(header.fetchSemanticsNode().boundsInRoot.top > raised + 60f, "Downward unconsumed scroll must move the sheet")
            list.performTouchInput { up() }
        }

    @Test fun exposedMapReceivesGesturesWithoutAnInterceptingOverlay() =
        runDesktopComposeUiTest(width = 411, height = 800) {
            var mapTaps = 0
            setContent {
                LogDateTheme {
                    WorkspaceSupportingSheet(
                        "Summary",
                        initialExtent = SupportingExtent.Browsing,
                        header = { Text("Browse places") },
                        focus = { Box(Modifier.fillMaxSize().testTag("map").clickable { mapTaps++ }) },
                        supporting = { Text("Selected place") },
                    )
                }
            }
            onNodeWithTag("map").performTouchInput {
                down(Offset(100f, 100f))
                up()
            }
            assertEquals(1, mapTaps)
        }

    @Test fun contentReleasePublishesTheSettledExtent() =
        runDesktopComposeUiTest(width = 411, height = 800) {
            setContent {
                LogDateTheme {
                    WorkspaceSupportingSheet(
                        "Summary",
                        initialExtent = SupportingExtent.Browsing,
                        header = { Text("Browse places") },
                        focus = {},
                        supporting = {
                            LazyColumn(Modifier.fillMaxSize().testTag("places_list")) {
                                items(30) { Text("Place $it", Modifier.height(64.dp)) }
                            }
                        },
                    )
                }
            }
            onNodeWithTag("places_list").performTouchInput {
                down(Offset(center.x, height - 30f))
                moveBy(Offset(0f, -210f), 800)
                advanceEventTime(200)
                up()
            }
            onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded")).fetchSemanticsNode()
        }

    @Test fun shortResizePreservesSelectedContentAndMapComposition() =
        runDesktopComposeUiTest(width = 411, height = 800) {
            var height by mutableStateOf(800.dp)
            var mapCompositions = 0
            setContent {
                LogDateTheme {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.height(height)) {
                            WorkspaceSupportingSheet(
                                "Summary",
                                initialExtent = SupportingExtent.Browsing,
                                header = { Text("Browse places") },
                                focus = {
                                    androidx.compose.runtime.DisposableEffect(Unit) {
                                        mapCompositions++
                                        onDispose { }
                                    }
                                },
                                supporting = { Text("Selected place") },
                            )
                        }
                    }
                }
            }
            height = 300.dp
            onNodeWithText("Selected place").fetchSemanticsNode()
            height = 800.dp
            onNodeWithText("Selected place").fetchSemanticsNode()
            assertEquals(1, mapCompositions, "Resize must not remount the map or reset its selection")
        }

    @Test fun reducedMotionKeepsDirectFingerTracking() =
        runDesktopComposeUiTest(width = 411, height = 800) {
            setContent {
                CompositionLocalProvider(LocalReduceMotionOverride provides true) {
                    LogDateTheme {
                        WorkspaceSupportingSheet(
                            "Summary",
                            initialExtent = SupportingExtent.Browsing,
                            header = { Text("Browse places") },
                            focus = {},
                            supporting = { Text("Selected place") },
                        )
                    }
                }
            }
            val header = onNodeWithText("Browse places")
            val start = header.fetchSemanticsNode().boundsInRoot.top
            header.performTouchInput {
                down(center)
                moveBy(Offset(0f, -100f), 300)
            }
            assertTrue(header.fetchSemanticsNode().boundsInRoot.top < start - 60f)
            header.performTouchInput { up() }
        }

    @Test fun aContentDragInterruptsAnInFlightHeaderFling() =
        runDesktopComposeUiTest(width = 411, height = 800) {
            setContent {
                LogDateTheme {
                    WorkspaceSupportingSheet(
                        "Summary",
                        initialExtent = SupportingExtent.Browsing,
                        header = { Text("Browse places") },
                        focus = {},
                        supporting = {
                            LazyColumn(Modifier.fillMaxSize().testTag("places_list")) {
                                items(30) { Text("Place $it", Modifier.height(64.dp)) }
                            }
                        },
                    )
                }
            }
            mainClock.autoAdvance = false
            val header = onNodeWithText("Browse places")
            header.performTouchInput { swipeWithVelocity(center, center + Offset(0f, -70f), 1200f, 160) }
            mainClock.advanceTimeBy(32)
            val list = onNodeWithTag("places_list")
            list.performTouchInput { down(Offset(center.x, 50f)) }
            val before = header.fetchSemanticsNode().boundsInRoot.top
            mainClock.advanceTimeBy(64)
            assertEquals(before, header.fetchSemanticsNode().boundsInRoot.top, 1f, "Touching content must stop an ongoing fling")
            list.performTouchInput { moveBy(Offset(0f, 60f), 64) }
            mainClock.advanceTimeBy(64)
            val after = header.fetchSemanticsNode().boundsInRoot.top
            assertTrue(after > before + 35f, "New finger input must own motion instead of fighting the fling: $before -> $after")
            list.performTouchInput { up() }
            mainClock.autoAdvance = true
        }

    @Test fun unavailableFullViewDoesNotLeaveAnImpossibleExtent() =
        runDesktopComposeUiTest(width = 411, height = 800) {
            setContent {
                LogDateTheme {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.height(48.dp).testTag("tiny_sheet")) {
                            WorkspaceSupportingSheet("Summary", header = { Text("Browse places") }, focus = {}, supporting = {})
                        }
                    }
                }
            }
            assertEquals(48f, onNodeWithTag("tiny_sheet").fetchSemanticsNode().boundsInRoot.height)
            onNodeWithContentDescription("Full view").performClick()
            onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Peek")).fetchSemanticsNode()
        }
}
