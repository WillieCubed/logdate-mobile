@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.client.datastore.EntryStartMode
import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.editor.ui.content.EarlierEntryContext
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.HandwritingBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.InkPoint
import app.logdate.feature.postcards.model.InkTool
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import app.logdate.ui.theme.LogDateTheme
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Design study using the editor's actual block sequence, controls, canvas, and focus motion.
 * Local preview state never writes a draft or changes the user's configured default.
 */
@Composable
internal fun EntryPageStudy(
    page: HandwritingPage = entryPageStudyInk(),
    focused: Boolean = false,
    expandedMemories: Boolean = false,
    dark: Boolean = false,
    fold: FoldableLayoutInfo = FoldableLayoutInfo(),
    defaultMode: EntryStartMode = EntryStartMode.TEXT,
    initialBlocks: List<EntryBlockUiState>? = null,
    onBlocksChanged: (List<EntryBlockUiState>) -> Unit = {},
    overviewStateOverride: EntryPageOverviewState? = null,
) {
    val ink = remember { HandwritingBlockUiState(page = page) }
    val text = remember { TextBlockUiState(content = "Took the long way home.\n\nThe light was too good to miss.") }
    val overviewState = remember { overviewStateOverride ?: EntryPageOverviewState(expandedMemories) }
    val earlier =
        remember {
            listOf(
                JournalNote.Text(
                    content = "Coffee outside before the day got busy. A little more time to myself this morning.",
                    creationTimestamp = Instant.parse("2026-10-26T15:12:00Z"),
                    lastUpdated = Instant.parse("2026-10-26T15:12:00Z"),
                ),
                JournalNote.Text(
                    content =
                        "Lunch ran longer than planned. Glad it did. " +
                            "We ended up walking through the park instead of heading straight back.",
                    creationTimestamp = Instant.parse("2026-10-26T19:46:00Z"),
                    lastUpdated = Instant.parse("2026-10-26T19:46:00Z"),
                ),
            )
        }
    provideFoldableLayoutInfo(fold) {
        LogDateTheme(darkTheme = dark, dynamicColor = false) {
            EntryStartPrototype(
                initialBlocks = initialBlocks ?: listOf(text, ink),
                onBlocksChanged = onBlocksChanged,
                initialFocusedBlockId = if (focused) ink.id else null,
                initiallyFocused = focused,
                focusAnyBlock = true,
                continuousOverview = true,
                overviewStudyState = overviewState,
                studyDefaultMode = defaultMode,
                entryContextOverride = { EarlierEntryContext(earlier, expanded = expandedMemories) },
                overviewContent = { EntryPageOverview(it, earlier, overviewState, defaultMode) },
            )
        }
    }
}

internal fun entryPageStudyInk(): HandwritingPage {
    val paths =
        listOf(
            listOf(65f to 230f, 175f to 105f, 245f to 170f, 330f to 70f, 510f to 230f),
            listOf(65f to 230f, 510f to 230f),
            listOf(155f to 128f, 175f to 145f, 194f to 125f),
            listOf(303f to 102f, 331f to 125f, 353f to 95f),
            listOf(240f to 288f, 283f to 267f, 325f to 288f, 362f to 269f, 405f to 289f),
        )
    return HandwritingPage(
        strokes =
            paths.map { path ->
                CanvasElement.Ink(Uuid.random(), InkTool.PEN, "#253F99", 4f, path.map { (x, y) -> InkPoint(x, y, 0.7f) })
            },
    )
}

@Preview(name = "Entry page · phone", widthDp = 360, heightDp = 800)
@Composable
private fun EntryPagePhonePreview() = EntryPageStudy()

@Preview(name = "Entry page · tablet", widthDp = 1280, heightDp = 800)
@Composable
private fun EntryPageTabletPreview() = EntryPageStudy()

@Preview(name = "Earlier today · expanded", widthDp = 360, heightDp = 800)
@Composable
private fun EntryPageMemoriesPreview() = EntryPageStudy(expandedMemories = true)

@Preview(name = "Handwriting · focus", widthDp = 360, heightDp = 800)
@Composable
private fun EntryPageFocusPreview() = EntryPageStudy(focused = true)

@Preview(name = "Entry page · dark", widthDp = 360, heightDp = 800)
@Composable
private fun EntryPageDarkPreview() = EntryPageStudy(dark = true)

@Preview(name = "Entry page · book posture", widthDp = 1000, heightDp = 800)
@Composable
private fun EntryPageBookPreview() = EntryPageStudy(fold = entryPageStudyFold(book = true))

@Preview(name = "Handwriting · tabletop posture", widthDp = 1000, heightDp = 800)
@Composable
private fun EntryPageTabletopPreview() = EntryPageStudy(focused = true, fold = entryPageStudyFold(book = false))

private fun entryPageStudyFold(book: Boolean): FoldableLayoutInfo =
    FoldableLayoutInfo(
        isFoldable = true,
        posture = if (book) FoldablePosture.Book else FoldablePosture.Tabletop,
        hinge =
            FoldableHingeInfo(
                orientation = if (book) FoldableHingeOrientation.Vertical else FoldableHingeOrientation.Horizontal,
                state = FoldableHingeState.HalfOpened,
                occlusionType = FoldableOcclusionType.Full,
                bounds =
                    if (book) {
                        FoldableHingeBounds(488.dp, 0.dp, 512.dp, 800.dp, 24.dp, 800.dp)
                    } else {
                        FoldableHingeBounds(0.dp, 388.dp, 1000.dp, 412.dp, 1000.dp, 24.dp)
                    },
                isSeparating = true,
            ),
    )
