package app.logdate.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

/**
 * Renders persisted journal text with the same Markdown vocabulary used by the entry editor.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun MarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val typography = MaterialTheme.typography
    Markdown(
        content = content,
        typography =
            markdownTypography(
                h1 = typography.headlineMedium,
                h2 = typography.headlineSmall,
                h3 = typography.titleLarge,
                h4 = typography.titleMedium,
                h5 = typography.titleSmall,
                h6 = typography.labelLarge,
                text = textStyle,
                paragraph = textStyle,
                ordered = textStyle,
                bullet = textStyle,
                list = textStyle,
                table = textStyle,
            ),
        modifier = modifier,
    )
}

/**
 * Renders a compact, line-safe Markdown summary for cards and lists.
 *
 * A single text layout owns the line limit so glyphs are ellipsized instead of clipping independent
 * Markdown blocks. Its semantics are also limited to the characters that remain visible.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun MarkdownPreviewText(
    content: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = 4,
) {
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    val styles =
        remember(typography, colors) {
            MarkdownPreviewStyles(
                h1 = typography.headlineMedium.toSpanStyle(),
                h2 = typography.headlineSmall.toSpanStyle(),
                h3 = typography.titleLarge.toSpanStyle(),
                h4 = typography.titleMedium.toSpanStyle(),
                h5 = typography.titleSmall.toSpanStyle(),
                h6 = typography.labelLarge.toSpanStyle(),
                strong = SpanStyle(fontWeight = FontWeight.Bold),
                emphasis = SpanStyle(fontStyle = FontStyle.Italic),
                strikethrough = SpanStyle(textDecoration = TextDecoration.LineThrough),
                code =
                    SpanStyle(
                        color = colors.onSurfaceVariant,
                        background = colors.surfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    ),
                link =
                    SpanStyle(
                        color = colors.primary,
                        textDecoration = TextDecoration.Underline,
                    ),
                quote =
                    SpanStyle(
                        color = colors.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                    ),
            )
        }
    val preview = remember(content, styles) { buildMarkdownPreview(content, styles) }
    var semanticsText by remember(preview) { mutableStateOf(preview) }

    Text(
        text = preview,
        style = textStyle,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            val visibleEnd =
                if (result.lineCount == 0) {
                    0
                } else {
                    result.getLineEnd(result.lineCount - 1, visibleEnd = true)
                }
            val updatedSemantics =
                previewSemanticsText(
                    content = preview,
                    visibleEnd = visibleEnd,
                    hasVisualOverflow = result.hasVisualOverflow,
                )
            if (updatedSemantics != semanticsText) {
                semanticsText = updatedSemantics
            }
        },
        modifier =
            modifier.clearAndSetSemantics {
                text = semanticsText
            },
    )
}

internal data class MarkdownPreviewStyles(
    val h1: SpanStyle,
    val h2: SpanStyle,
    val h3: SpanStyle,
    val h4: SpanStyle,
    val h5: SpanStyle,
    val h6: SpanStyle,
    val strong: SpanStyle,
    val emphasis: SpanStyle,
    val strikethrough: SpanStyle,
    val code: SpanStyle,
    val link: SpanStyle,
    val quote: SpanStyle,
)

internal fun buildMarkdownPreview(
    content: String,
    styles: MarkdownPreviewStyles,
): AnnotatedString {
    if (content.isBlank()) return AnnotatedString("")

    val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(content)
    return MarkdownPreviewBuilder(content, styles).build(root)
}

internal fun previewSemanticsText(
    content: AnnotatedString,
    visibleEnd: Int,
    hasVisualOverflow: Boolean,
): AnnotatedString {
    if (!hasVisualOverflow) return content

    var end = visibleEnd.coerceIn(0, content.length)
    while (end > 0 && content[end - 1].isWhitespace()) end--
    val visibleContent = content.subSequence(0, end)
    return AnnotatedString.Builder(visibleContent).apply { append('…') }.toAnnotatedString()
}

private class MarkdownPreviewBuilder(
    private val source: String,
    private val styles: MarkdownPreviewStyles,
) {
    private val text = StringBuilder()
    private val spans = mutableListOf<PreviewSpan>()

    fun build(root: ASTNode): AnnotatedString {
        renderBlocks(root.children)
        trimEnd()

        val result = AnnotatedString.Builder(text.toString())
        spans.forEach { span ->
            val end = span.end.coerceAtMost(text.length)
            if (span.start < end) {
                result.addStyle(span.style, span.start, end)
            }
        }
        return result.toAnnotatedString()
    }

    private fun renderBlocks(nodes: List<ASTNode>) {
        nodes.filterNot { it.type == MarkdownTokenTypes.EOL }.forEach { node ->
            val start = text.length
            render(node)
            if (text.length > start) newLine()
        }
    }

    private fun render(node: ASTNode) {
        when (node.type) {
            MarkdownElementTypes.ATX_1 -> renderHeading(node, styles.h1, MarkdownTokenTypes.ATX_CONTENT)
            MarkdownElementTypes.ATX_2 -> renderHeading(node, styles.h2, MarkdownTokenTypes.ATX_CONTENT)
            MarkdownElementTypes.ATX_3 -> renderHeading(node, styles.h3, MarkdownTokenTypes.ATX_CONTENT)
            MarkdownElementTypes.ATX_4 -> renderHeading(node, styles.h4, MarkdownTokenTypes.ATX_CONTENT)
            MarkdownElementTypes.ATX_5 -> renderHeading(node, styles.h5, MarkdownTokenTypes.ATX_CONTENT)
            MarkdownElementTypes.ATX_6 -> renderHeading(node, styles.h6, MarkdownTokenTypes.ATX_CONTENT)
            MarkdownElementTypes.SETEXT_1 -> renderHeading(node, styles.h1, MarkdownTokenTypes.SETEXT_CONTENT)
            MarkdownElementTypes.SETEXT_2 -> renderHeading(node, styles.h2, MarkdownTokenTypes.SETEXT_CONTENT)
            MarkdownElementTypes.STRONG -> styled(styles.strong) { renderChildren(node) }
            MarkdownElementTypes.EMPH -> styled(styles.emphasis) { renderChildren(node) }
            GFMElementTypes.STRIKETHROUGH -> styled(styles.strikethrough) { renderChildren(node) }
            MarkdownElementTypes.CODE_SPAN -> styled(styles.code) { renderChildren(node) }
            MarkdownElementTypes.CODE_FENCE,
            MarkdownElementTypes.CODE_BLOCK,
            -> renderCode(node)
            MarkdownElementTypes.BLOCK_QUOTE -> styled(styles.quote) { renderChildren(node) }
            MarkdownElementTypes.INLINE_LINK,
            MarkdownElementTypes.FULL_REFERENCE_LINK,
            MarkdownElementTypes.SHORT_REFERENCE_LINK,
            -> renderLink(node)
            MarkdownElementTypes.AUTOLINK -> styled(styles.link) { renderChildren(node) }
            MarkdownElementTypes.IMAGE -> renderImage(node)
            MarkdownElementTypes.LINK_DEFINITION,
            MarkdownElementTypes.LINK_DESTINATION,
            MarkdownElementTypes.LINK_TITLE,
            -> Unit
            MarkdownElementTypes.UNORDERED_LIST,
            MarkdownElementTypes.ORDERED_LIST,
            -> renderList(node)
            MarkdownTokenTypes.HORIZONTAL_RULE -> appendInline("—")
            else -> {
                if (node.children.isEmpty()) {
                    renderLeaf(node)
                } else {
                    renderChildren(node)
                }
            }
        }
    }

    private fun renderHeading(
        node: ASTNode,
        style: SpanStyle,
        contentType: org.intellij.markdown.IElementType,
    ) {
        node.children.firstOrNull { it.type == contentType }?.let { content ->
            styled(style) { renderChildren(content) }
        }
    }

    private fun renderCode(node: ASTNode) {
        val codeNodes =
            node.descendants().filter {
                it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT ||
                    it.type == MarkdownTokenTypes.CODE_LINE
            }
        styled(styles.code) {
            codeNodes.forEachIndexed { index, codeNode ->
                if (index > 0) newLine()
                appendCode(source.substring(codeNode.startOffset, codeNode.endOffset))
            }
        }
    }

    private fun renderLink(node: ASTNode) {
        val label = node.descendants().firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
        if (label != null) {
            styled(styles.link) { renderChildren(label) }
        } else {
            styled(styles.link) { renderChildren(node) }
        }
    }

    private fun renderImage(node: ASTNode) {
        node.descendants().firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }?.let(::renderChildren)
    }

    private fun renderList(node: ASTNode) {
        node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }.forEachIndexed { index, item ->
            if (index > 0) newLine()
            renderChildren(item)
        }
    }

    private fun renderChildren(node: ASTNode) {
        node.children.forEach(::render)
    }

    private fun renderLeaf(node: ASTNode) {
        when (node.type) {
            MarkdownTokenTypes.TEXT,
            MarkdownTokenTypes.WHITE_SPACE,
            MarkdownTokenTypes.URL,
            MarkdownTokenTypes.AUTOLINK,
            GFMTokenTypes.GFM_AUTOLINK,
            -> appendInline(source.substring(node.startOffset, node.endOffset))
            MarkdownTokenTypes.CODE_FENCE_CONTENT,
            MarkdownTokenTypes.CODE_LINE,
            -> appendCode(source.substring(node.startOffset, node.endOffset))
            MarkdownTokenTypes.HARD_LINE_BREAK -> newLine()
            MarkdownTokenTypes.EOL -> appendInline(" ")
            MarkdownTokenTypes.LIST_BULLET -> appendInline("• ")
            MarkdownTokenTypes.LIST_NUMBER -> appendInline(source.substring(node.startOffset, node.endOffset))
        }
    }

    private fun appendInline(value: String) {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char == '\\' && index + 1 < value.length && value[index + 1] in ESCAPABLE_MARKDOWN -> {
                    text.append(value[index + 1])
                    index++
                }
                char.isWhitespace() -> {
                    if (text.isNotEmpty() && !text.last().isWhitespace()) text.append(' ')
                }
                else -> text.append(char)
            }
            index++
        }
    }

    private fun appendCode(value: String) {
        value.forEach { char ->
            if (char == '\r') return@forEach
            text.append(char)
        }
    }

    private fun styled(
        style: SpanStyle,
        block: () -> Unit,
    ) {
        val start = text.length
        block()
        val end = text.length
        if (start < end && style != SpanStyle()) {
            spans += PreviewSpan(style, start, end)
        }
    }

    private fun newLine() {
        trimTrailingSpaces()
        if (text.isNotEmpty() && text.last() != '\n') text.append('\n')
    }

    private fun trimTrailingSpaces() {
        while (text.isNotEmpty() && text.last() != '\n' && text.last().isWhitespace()) {
            text.deleteAt(text.lastIndex)
        }
    }

    private fun trimEnd() {
        while (text.isNotEmpty() && text.last().isWhitespace()) {
            text.deleteAt(text.lastIndex)
        }
    }

    private fun ASTNode.descendants(): Sequence<ASTNode> =
        children.asSequence().flatMap { child -> sequenceOf(child) + child.descendants() }

    private data class PreviewSpan(
        val style: SpanStyle,
        val start: Int,
        val end: Int,
    )

    private companion object {
        val ESCAPABLE_MARKDOWN = setOf('\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!', '>', '~')
    }
}
