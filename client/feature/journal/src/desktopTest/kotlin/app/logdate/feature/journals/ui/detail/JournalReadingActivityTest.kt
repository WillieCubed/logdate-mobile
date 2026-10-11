package app.logdate.feature.journals.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
import app.logdate.ui.workspace.LocalWorkspaceEnabled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class JournalReadingActivityTest {
    @Test
    fun wideJournalShowsContextBesideItsOwnContentsWithEitherFraming() {
        listOf(false, true).forEach { workspace ->
            runDesktopComposeUiTest(width = 960, height = 800) {
                setContent {
                    LogDateTheme {
                        CompositionLocalProvider(LocalWorkspaceEnabled provides workspace) {
                            JournalDetailScreenContent(state, onGoBack = {})
                        }
                    }
                }
                onNodeWithText("3 items").assertIsDisplayed()
                val context = onNodeWithTag("journal_reading_context").fetchSemanticsNode().boundsInRoot
                val contents = onNodeWithTag("journal_reading_contents").fetchSemanticsNode().boundsInRoot
                assertTrue(context.right <= contents.left, "Context and contents must occupy distinct useful panes")
                assertTrue(context.width >= 288f && contents.width >= 480f, "Both panes must retain useful reading widths")
                onNodeWithText("We followed the trail past the old lookout.").assertIsDisplayed()
            }
        }
    }

    @Test
    fun wideIdentityAppearsOnceAndBackRemainsAtTheLeadingEdge() {
        listOf(false, true).forEach { workspace ->
            runDesktopComposeUiTest(width = 960, height = 800) {
                setContent {
                    LogDateTheme {
                        CompositionLocalProvider(LocalWorkspaceEnabled provides workspace) {
                            JournalDetailScreenContent(state, onGoBack = {})
                        }
                    }
                }
                onAllNodesWithText(state.title).assertCountEquals(1)
                val back = onNodeWithContentDescription(if (workspace) "Back to journals" else "Back").fetchSemanticsNode().boundsInRoot
                assertTrue(back.left < 60f, "Back belongs at the leading edge of the reading activity")
            }
        }
    }

    @Test
    fun narrowRelatedPaneDoesNotAddAnotherContextColumn() =
        runDesktopComposeUiTest(width = 480, height = 800) {
            var opened: Uuid? = null
            setContent {
                LogDateTheme {
                    CompositionLocalProvider(LocalWorkspaceEnabled provides true) {
                        JournalDetailScreenContent(state, onGoBack = {}, onNavigateToNoteDetail = { opened = it })
                    }
                }
            }
            onNodeWithTag("journal_reading_context").assertDoesNotExist()
            onAllNodesWithText(state.title).assertCountEquals(1)
            onNodeWithText("We followed the trail past the old lookout.").performClick()
            assertEquals(null, opened)
            onNodeWithText("We followed the trail past the old lookout.").performClick()
            assertEquals(state.entries.first().id, opened)
        }

    @Test
    fun expandedContentSurvivesNarrowingTheReadingPane() =
        runDesktopComposeUiTest(width = 960, height = 800) {
            var width by mutableStateOf(960.dp)
            var opened: Uuid? = null
            setContent {
                LogDateTheme {
                    CompositionLocalProvider(LocalWorkspaceEnabled provides true) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.width(width).fillMaxHeight()) {
                                JournalDetailScreenContent(state, onGoBack = {}, onNavigateToNoteDetail = { opened = it })
                            }
                        }
                    }
                }
            }
            onNodeWithText("We followed the trail past the old lookout.").performClick()
            assertEquals(null, opened)
            runOnIdle { width = 480.dp }
            onNodeWithTag("journal_reading_context").assertDoesNotExist()
            onNodeWithText("We followed the trail past the old lookout.").performClick()
            assertEquals(state.entries.first().id, opened)
        }

    @Test
    fun largeTextKeepsContentsReadableInsteadOfForcingTwoColumns() =
        runDesktopComposeUiTest(width = 960, height = 800) {
            setContent {
                LogDateTheme {
                    CompositionLocalProvider(LocalWorkspaceEnabled provides true, LocalDensity provides Density(1f, 2f)) {
                        JournalDetailScreenContent(state, onGoBack = {})
                    }
                }
            }
            onNodeWithContentDescription("Back to journals").assertIsDisplayed()
            onNodeWithContentDescription("Add to journal").assertIsDisplayed()
            onNodeWithContentDescription("Journal options").assertIsDisplayed()
            onNodeWithTag("journal_reading_context").assertDoesNotExist()
            onNodeWithText("We followed the trail past the old lookout.").assertIsDisplayed()
        }

    @Test
    fun legacyLargeTextKeepsTheCompleteIdentityVisibleAboveReadableContents() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            setContent {
                LogDateTheme {
                    CompositionLocalProvider(LocalWorkspaceEnabled provides false, LocalDensity provides Density(1f, 2f)) {
                        JournalDetailScreenContent(state, onGoBack = {})
                    }
                }
            }
            val title = onNodeWithText(state.title)
            val results = mutableListOf<TextLayoutResult>()
            title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(results) }
            assertTrue(results.isNotEmpty())
            assertFalse(results.any { it.hasVisualOverflow }, "Large text must not truncate the journal identity")
            val full = title.getUnclippedBoundsInRoot()
            val visible = title.fetchSemanticsNode().boundsInRoot
            assertEquals((full.bottom - full.top).value, visible.height, 1f, "The app bar must not clip the wrapping title")
            val contents = onNodeWithTag("journal_reading_contents").fetchSemanticsNode().boundsInRoot
            assertTrue(full.bottom.value <= contents.top, "The complete title belongs above the contents")
            assertTrue(contents.height >= 300f, "Large text must leave meaningful space for reading")
        }

    @Test
    fun narrowRtlBookPlacesContentsInsideTheLargerPhysicalRegion() =
        runDesktopComposeUiTest(width = 960, height = 800) {
            setContent {
                LogDateTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        provideFoldableLayoutInfo(
                            FoldableLayoutInfo(
                                true,
                                FoldablePosture.Book,
                                FoldableHingeInfo(
                                    FoldableHingeOrientation.Vertical,
                                    FoldableHingeState.HalfOpened,
                                    FoldableOcclusionType.Full,
                                    FoldableHingeBounds(450.dp, 0.dp, 474.dp, 800.dp, 24.dp, 800.dp),
                                    true,
                                ),
                            ),
                        ) {
                            JournalReadingLayout(context = { Text("Context") }, contents = { Text("Contents") })
                        }
                    }
                }
            }
            onNodeWithTag("journal_reading_context").assertDoesNotExist()
            val contents = onNodeWithTag("journal_reading_contents").fetchSemanticsNode().boundsInRoot
            assertEquals(474f, contents.left)
            assertTrue(contents.right <= 960f, "Contents must not be offset beyond the activity in RTL")
        }

    @Test
    fun narrowRtlBookKeepsTheCompleteJournalHeaderInTheLargerPhysicalRegion() =
        runDesktopComposeUiTest(width = 960, height = 800) {
            setContent {
                LogDateTheme {
                    CompositionLocalProvider(LocalWorkspaceEnabled provides true, LocalLayoutDirection provides LayoutDirection.Rtl) {
                        provideFoldableLayoutInfo(
                            FoldableLayoutInfo(
                                true,
                                FoldablePosture.Book,
                                FoldableHingeInfo(
                                    FoldableHingeOrientation.Vertical,
                                    FoldableHingeState.HalfOpened,
                                    FoldableOcclusionType.Full,
                                    FoldableHingeBounds(450.dp, 0.dp, 474.dp, 800.dp, 24.dp, 800.dp),
                                    true,
                                ),
                            ),
                        ) {
                            JournalDetailScreenContent(state, onGoBack = {})
                        }
                    }
                }
            }
            val identity = onNodeWithText(state.title).fetchSemanticsNode().boundsInRoot
            assertTrue(identity.left >= 474f && identity.right <= 960f, "The full journal identity must stay clear of the hinge")
            val contents = onNodeWithTag("journal_reading_contents").fetchSemanticsNode().boundsInRoot
            assertEquals(474f, contents.left)
            assertTrue(contents.right <= 960f && contents.width >= 480f, "Local hinge translation must not offset the contents twice")
            listOf("Back to journals", "Add to journal", "Journal options").forEach { label ->
                val action = onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
                assertTrue(action.left >= 474f && action.right <= 960f, "$label must stay in the selected physical region")
            }
        }

    companion object {
        internal val state =
            JournalDetailUiState.Success(
                journalId = Uuid.parse("00000000-0000-0000-0000-000000000601"),
                title = "Weekend field notes from the mountains and everything we found along the way",
                entries =
                    listOf(
                        EntryDisplayData.TextEntry(
                            Uuid.parse("00000000-0000-0000-0000-000000000602"),
                            Instant.parse("2026-10-08T18:00:00Z"),
                            "We followed the trail past the old lookout.",
                        ),
                        EntryDisplayData.TextEntry(
                            Uuid.parse("00000000-0000-0000-0000-000000000603"),
                            Instant.parse("2026-10-07T16:00:00Z"),
                            "Coffee at the trailhead, with the whole morning ahead of us.",
                        ),
                        EntryDisplayData.TextEntry(
                            Uuid.parse("00000000-0000-0000-0000-000000000604"),
                            Instant.parse("2026-10-06T20:00:00Z"),
                            "The sky cleared just before we reached the ridge.",
                        ),
                    ),
            )
    }
}
