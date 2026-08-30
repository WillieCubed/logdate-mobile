package app.logdate.feature.editor.ui.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

internal enum class InlineMarkdownStyle {
    HEADING_1,
    HEADING_2,
    HEADING_3,
    HEADING_4,
    HEADING_5,
    HEADING_6,
    STRONG,
    EMPHASIS,
    STRIKETHROUGH,
    INLINE_CODE,
    CODE_BLOCK,
    LINK,
    BLOCK_QUOTE,
    LIST_MARKER,
}

internal data class InlineMarkdownSpan(
    val style: InlineMarkdownStyle,
    val start: Int,
    val end: Int,
)

internal fun styleInlineMarkdown(
    text: String,
    styleFor: (InlineMarkdownStyle) -> SpanStyle,
): AnnotatedString {
    val styledText = AnnotatedString.Builder(text)
    parseInlineMarkdown(text).forEach { span ->
        styledText.addStyle(
            style = styleFor(span.style),
            start = span.start,
            end = span.end,
        )
    }
    return styledText.toAnnotatedString()
}

internal class InlineMarkdownVisualTransformation(
    private val styleFor: (InlineMarkdownStyle) -> SpanStyle,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(
            text = styleInlineMarkdown(text.text, styleFor),
            offsetMapping = OffsetMapping.Identity,
        )
}

internal fun parseInlineMarkdown(text: String): List<InlineMarkdownSpan> {
    if (text.isEmpty()) return emptyList()

    val spans = mutableListOf<InlineMarkdownSpan>()
    val codeRanges = mutableListOf<TextRange>()
    val fencedCodeRanges = mutableListOf<TextRange>()
    val strongRanges = mutableListOf<TextRange>()

    fun addSpan(
        style: InlineMarkdownStyle,
        start: Int,
        end: Int,
    ) {
        if (start in 0..<end && end <= text.length) {
            spans += InlineMarkdownSpan(style = style, start = start, end = end)
        }
    }

    findFencedCodeRanges(text).forEach { fencedCode ->
        addSpan(InlineMarkdownStyle.CODE_BLOCK, fencedCode.content.start, fencedCode.content.end)
        codeRanges += fencedCode.full
        fencedCodeRanges += fencedCode.full
    }

    findDelimitedRanges(
        text = text,
        delimiter = "`",
        excludedRanges = codeRanges,
    ).forEach { inlineCode ->
        addSpan(InlineMarkdownStyle.INLINE_CODE, inlineCode.content.start, inlineCode.content.end)
        codeRanges += inlineCode.full
    }

    forEachLine(text) { lineStart, lineEnd ->
        if (lineStart >= lineEnd || fencedCodeRanges.intersects(lineStart, lineEnd)) return@forEachLine

        val contentStart = text.firstNonWhitespaceIndex(lineStart, lineEnd)
        if (contentStart >= lineEnd) return@forEachLine

        val headingLevel = text.headingLevelAt(contentStart, lineEnd)
        if (headingLevel != null) {
            val headingStart = text.firstNonWhitespaceIndex(contentStart + headingLevel + 1, lineEnd)
            val headingEnd = text.lastNonWhitespaceEnd(headingStart, lineEnd)
            addSpan(headingStyle(headingLevel), headingStart, headingEnd)
            return@forEachLine
        }

        if (text[contentStart] == '>') {
            val quoteStart = text.firstNonWhitespaceIndex(contentStart + 1, lineEnd)
            val quoteEnd = text.lastNonWhitespaceEnd(quoteStart, lineEnd)
            addSpan(InlineMarkdownStyle.BLOCK_QUOTE, quoteStart, quoteEnd)
            return@forEachLine
        }

        val listMarkerEnd = text.listMarkerEnd(contentStart, lineEnd)
        if (listMarkerEnd != null) {
            addSpan(InlineMarkdownStyle.LIST_MARKER, contentStart, listMarkerEnd)
        }
    }

    LINK_PATTERN.findAll(text).forEach { match ->
        val label = match.groups[1] ?: return@forEach
        if (!isEscaped(text, match.range.first) && !codeRanges.intersects(match.range)) {
            val labelStart = match.range.first + LINK_LABEL_OFFSET
            addSpan(InlineMarkdownStyle.LINK, labelStart, labelStart + label.value.length)
        }
    }

    findDelimitedRanges(
        text = text,
        delimiter = "***",
        excludedRanges = codeRanges,
    ).forEach { strongEmphasis ->
        addSpan(InlineMarkdownStyle.STRONG, strongEmphasis.content.start, strongEmphasis.content.end)
        addSpan(InlineMarkdownStyle.EMPHASIS, strongEmphasis.content.start, strongEmphasis.content.end)
        strongRanges += strongEmphasis.full
    }

    STRONG_PATTERN.findAll(text).forEach { match ->
        val content = match.groups[1] ?: match.groups[2] ?: return@forEach
        val hasValidBoundary =
            match.groups[2] == null ||
                hasUnderscoreBoundary(text, match.range.first, match.range.last + 1)
        if (
            hasValidBoundary &&
            !isEscaped(text, match.range.first) &&
            !codeRanges.intersects(match.range) &&
            !strongRanges.intersects(match.range)
        ) {
            val strongStart = match.range.first + PAIRED_DELIMITER_OFFSET
            addSpan(InlineMarkdownStyle.STRONG, strongStart, strongStart + content.value.length)
            strongRanges += TextRange(match.range.first, match.range.last + 1)
        }
    }

    STRIKETHROUGH_PATTERN.findAll(text).forEach { match ->
        val content = match.groups[1] ?: return@forEach
        if (!isEscaped(text, match.range.first) && !codeRanges.intersects(match.range)) {
            val strikeStart = match.range.first + PAIRED_DELIMITER_OFFSET
            addSpan(InlineMarkdownStyle.STRIKETHROUGH, strikeStart, strikeStart + content.value.length)
        }
    }

    listOf("*", "_").forEach { delimiter ->
        findDelimitedRanges(
            text = text,
            delimiter = delimiter,
            excludedRanges = codeRanges + strongRanges,
            delimiterBoundary =
                if (delimiter == "_") {
                    { index, isOpening -> hasUnderscoreDelimiterBoundary(text, index, isOpening) }
                } else {
                    { _, _ -> true }
                },
        ).forEach { emphasis ->
            addSpan(InlineMarkdownStyle.EMPHASIS, emphasis.content.start, emphasis.content.end)
        }
    }

    return spans.sortedWith(compareBy(InlineMarkdownSpan::start, InlineMarkdownSpan::end, InlineMarkdownSpan::style))
}

private data class TextRange(
    val start: Int,
    val end: Int,
)

private data class DelimitedRange(
    val full: TextRange,
    val content: TextRange,
)

private fun findFencedCodeRanges(text: String): List<DelimitedRange> {
    val ranges = mutableListOf<DelimitedRange>()
    var searchFrom = 0
    while (searchFrom < text.length) {
        val opening = text.indexOf("```", startIndex = searchFrom)
        if (opening < 0) break
        if (isEscaped(text, opening)) {
            searchFrom = opening + 3
            continue
        }

        val contentStart = text.indexOf('\n', startIndex = opening + 3)
        if (contentStart < 0) break
        val closing = text.indexOf("```", startIndex = contentStart + 1)
        if (closing < 0) {
            ranges +=
                DelimitedRange(
                    full = TextRange(opening, text.length),
                    content = TextRange(contentStart + 1, text.length),
                )
            break
        }

        var contentEnd = closing
        while (contentEnd > contentStart + 1 && text[contentEnd - 1] == '\n') contentEnd--
        ranges +=
            DelimitedRange(
                full = TextRange(opening, closing + 3),
                content = TextRange(contentStart + 1, contentEnd),
            )
        searchFrom = closing + 3
    }
    return ranges
}

private fun findDelimitedRanges(
    text: String,
    delimiter: String,
    excludedRanges: List<TextRange>,
    delimiterBoundary: (index: Int, isOpening: Boolean) -> Boolean = { _, _ -> true },
): List<DelimitedRange> {
    val ranges = mutableListOf<DelimitedRange>()
    var searchFrom = 0
    while (searchFrom < text.length) {
        val opening = text.indexOf(delimiter, startIndex = searchFrom)
        if (opening < 0) break
        if (
            isEscaped(text, opening) ||
            excludedRanges.intersects(opening, opening + delimiter.length) ||
            !delimiterBoundary(opening, true)
        ) {
            searchFrom = opening + delimiter.length
            continue
        }

        val closing = text.indexOf(delimiter, startIndex = opening + delimiter.length)
        if (closing < 0) break
        if (text.substring(opening + delimiter.length, closing).contains('\n')) {
            searchFrom = opening + delimiter.length
            continue
        }
        if (
            isEscaped(text, closing) ||
            excludedRanges.intersects(closing, closing + delimiter.length) ||
            !delimiterBoundary(closing, false)
        ) {
            searchFrom = closing + delimiter.length
            continue
        }

        ranges +=
            DelimitedRange(
                full = TextRange(opening, closing + delimiter.length),
                content = TextRange(opening + delimiter.length, closing),
            )
        searchFrom = closing + delimiter.length
    }
    return ranges
}

private inline fun forEachLine(
    text: String,
    block: (start: Int, end: Int) -> Unit,
) {
    var lineStart = 0
    while (lineStart <= text.length) {
        val newline = text.indexOf('\n', startIndex = lineStart)
        val lineEnd = if (newline < 0) text.length else newline
        block(lineStart, lineEnd)
        if (newline < 0) break
        lineStart = newline + 1
    }
}

private fun String.firstNonWhitespaceIndex(
    start: Int,
    end: Int,
): Int {
    var index = start
    while (index < end && this[index].isWhitespace()) index++
    return index
}

private fun String.lastNonWhitespaceEnd(
    start: Int,
    end: Int,
): Int {
    var index = end
    while (index > start && this[index - 1].isWhitespace()) index--
    return index
}

private fun String.headingLevelAt(
    start: Int,
    end: Int,
): Int? {
    var index = start
    while (index < end && index - start < 6 && this[index] == '#') index++
    val level = index - start
    return level.takeIf { it in 1..6 && index < end && this[index].isWhitespace() }
}

private fun String.listMarkerEnd(
    start: Int,
    end: Int,
): Int? {
    if (start + 1 < end && this[start] in listOf('-', '+', '*') && this[start + 1].isWhitespace()) {
        return start + 1
    }

    var index = start
    while (index < end && this[index].isDigit()) index++
    return (index + 1)
        .takeIf { index > start && index < end && this[index] == '.' && it < end && this[it].isWhitespace() }
}

private fun headingStyle(level: Int): InlineMarkdownStyle =
    when (level) {
        1 -> InlineMarkdownStyle.HEADING_1
        2 -> InlineMarkdownStyle.HEADING_2
        3 -> InlineMarkdownStyle.HEADING_3
        4 -> InlineMarkdownStyle.HEADING_4
        5 -> InlineMarkdownStyle.HEADING_5
        else -> InlineMarkdownStyle.HEADING_6
    }

private fun List<TextRange>.intersects(range: IntRange): Boolean = intersects(range.first, range.last + 1)

private fun List<TextRange>.intersects(
    start: Int,
    end: Int,
): Boolean = any { range -> start < range.end && end > range.start }

private fun isEscaped(
    text: String,
    index: Int,
): Boolean {
    var slashCount = 0
    var cursor = index - 1
    while (cursor >= 0 && text[cursor] == '\\') {
        slashCount++
        cursor--
    }
    return slashCount % 2 == 1
}

private fun hasUnderscoreBoundary(
    text: String,
    start: Int,
    end: Int,
): Boolean =
    !text.getOrNull(start - 1).isMarkdownWordCharacter() &&
        !text.getOrNull(end).isMarkdownWordCharacter()

private fun hasUnderscoreDelimiterBoundary(
    text: String,
    index: Int,
    isOpening: Boolean,
): Boolean {
    val adjacent = if (isOpening) text.getOrNull(index - 1) else text.getOrNull(index + 1)
    return !adjacent.isMarkdownWordCharacter()
}

private fun Char?.isMarkdownWordCharacter(): Boolean = this != null && (isLetterOrDigit() || this == '_')

// `MatchGroup.range` is JVM/JS-only; the common standard library exposes ranges on
// `MatchResult` alone, so a capture group's start is derived from the width of the delimiter
// that opens it. Every pattern below opens its group at a fixed offset from the match.
private const val LINK_LABEL_OFFSET = 1
private const val PAIRED_DELIMITER_OFFSET = 2

private val LINK_PATTERN = Regex("\\[([^]\\n]+)]\\(([^)\\n]+)\\)")
private val STRONG_PATTERN = Regex("\\*\\*([^*\\n]+)\\*\\*|__([^_\\n]+)__")
private val STRIKETHROUGH_PATTERN = Regex("~~([^~\\n]+)~~")
