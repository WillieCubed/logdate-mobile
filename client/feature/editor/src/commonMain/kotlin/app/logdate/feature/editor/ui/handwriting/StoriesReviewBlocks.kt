package app.logdate.feature.editor.ui.handwriting

import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.HandwritingBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState

/** Keep additional review blocks while editing the first writing surfaces in capture mode. */
internal fun reconcileStoriesBlocks(
    blocks: List<EntryBlockUiState>,
    text: String,
    page: HandwritingPage,
): List<EntryBlockUiState> {
    val firstText = blocks.filterIsInstance<TextBlockUiState>().firstOrNull()
    val firstInk = blocks.filterIsInstance<HandwritingBlockUiState>().firstOrNull()
    return buildList {
        blocks.forEach { block ->
            add(
                when (block.id) {
                    firstText?.id -> firstText.copy(content = text)
                    firstInk?.id -> firstInk.copy(page = page)
                    else -> block
                },
            )
        }
        if (firstText == null && text.isNotBlank()) add(TextBlockUiState(content = text))
        if (firstInk == null && page.strokes.isNotEmpty()) add(HandwritingBlockUiState(page = page))
    }
}
