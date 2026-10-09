package app.logdate.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.LocalMarkdownPadding
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBackground
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import org.intellij.markdown.MarkdownElementTypes

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
    val defaultTypography = markdownTypography()
    val readingStyle = textStyle.copy(textDirection = TextDirection.Content)
    val styles = markdownPreviewStyles()
    val document = remember(content) { parseMarkdownDocument(content) }
    val readable = remember(document, styles) { buildMarkdownAnnotatedText(document, styles, clickableLinks = true) }
    Markdown(
        state = remember(document) { State.Success(document.root, document.source, linksLookedUp = false) },
        annotator =
            remember(document, readable) {
                markdownAnnotator(config = markdownAnnotatorConfig(eolAsNewLine = true)) { _, node ->
                    document.projections[node]?.let { range ->
                        if (!range.isEmpty()) append(readable.subSequence(range.first, range.last + 1))
                    }
                    true
                }
            },
        components =
            markdownComponents(
                codeFence = { MarkdownDocumentCode(document, readable, it) },
                codeBlock = { MarkdownDocumentCode(document, readable, it) },
                eol = { model ->
                    val range = document.projections[model.node]
                    if (range != null &&
                        !range.isEmpty() &&
                        (range.first == 0 || document.plainText.getOrNull(range.first - 1) == '\n')
                    ) {
                        Text("", style = readingStyle)
                    }
                },
                checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
                image = { model ->
                    document.projections[model.node]?.takeUnless { it.isEmpty() }?.let { range ->
                        Text(readable.subSequence(range.first, range.last + 1), style = readingStyle)
                    }
                },
                inlineImage = { model ->
                    document.projections[model.node]?.takeUnless { it.isEmpty() }?.let { range ->
                        Text(readable.subSequence(range.first, range.last + 1), style = readingStyle)
                    }
                },
                custom = { type, model ->
                    if (type == MarkdownElementTypes.HTML_BLOCK) {
                        Text(
                            document.source.substring(model.node.startOffset, model.node.endOffset),
                            style = readingStyle,
                        )
                    }
                },
            ),
        typography =
            markdownTypography(
                h1 = typography.headlineMedium.copy(textDirection = TextDirection.Content),
                h2 = typography.headlineSmall.copy(textDirection = TextDirection.Content),
                h3 = typography.titleLarge.copy(textDirection = TextDirection.Content),
                h4 = typography.titleMedium.copy(textDirection = TextDirection.Content),
                h5 = typography.titleSmall.copy(textDirection = TextDirection.Content),
                h6 = typography.labelLarge.copy(textDirection = TextDirection.Content),
                text = readingStyle,
                code = defaultTypography.code.copy(textDirection = TextDirection.Content),
                inlineCode = defaultTypography.inlineCode.copy(textDirection = TextDirection.Content),
                quote = defaultTypography.quote.copy(textDirection = TextDirection.Content),
                paragraph = readingStyle,
                ordered = readingStyle,
                bullet = readingStyle,
                list = readingStyle,
                table = readingStyle,
            ),
        modifier = modifier,
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun MarkdownDocumentCode(
    document: MarkdownDocument,
    readable: AnnotatedString,
    model: MarkdownComponentModel,
) {
    val range = document.projections[model.node]?.takeUnless { it.isEmpty() } ?: return
    MarkdownCodeBackground(
        color = LocalMarkdownColors.current.codeBackground,
        shape = RoundedCornerShape(LocalMarkdownDimens.current.codeBackgroundCornerSize),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Text(
            text = readable.subSequence(range.first, range.last + 1),
            style = model.typography.code,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(LocalMarkdownPadding.current.codeBlock),
        )
    }
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
    val styles = markdownPreviewStyles()
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

@Composable
private fun markdownPreviewStyles(): MarkdownPreviewStyles {
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    return remember(typography, colors) {
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
): AnnotatedString = buildMarkdownPreview(parseMarkdownDocument(content), styles)

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

internal fun buildMarkdownPreview(
    document: MarkdownDocument,
    styles: MarkdownPreviewStyles,
    clickableLinks: Boolean = false,
): AnnotatedString {
    val result = AnnotatedString.Builder(buildMarkdownAnnotatedText(document, styles, clickableLinks))
    if (document.plainText.isNotEmpty()) {
        result.addStyle(ParagraphStyle(textDirection = TextDirection.Content), 0, document.plainText.length)
    }
    return result.toAnnotatedString()
}

private fun buildMarkdownAnnotatedText(
    document: MarkdownDocument,
    styles: MarkdownPreviewStyles,
    clickableLinks: Boolean,
): AnnotatedString {
    val result = AnnotatedString.Builder(document.plainText)
    document.spans.forEach { span ->
        if (span.textStart < span.textEnd) {
            result.addStyle(styles.forKind(span.kind), span.textStart, span.textEnd)
            if (clickableLinks && span.kind == MarkdownStyleKind.LINK) {
                span.destination?.safeLinkDestination()?.let { destination ->
                    result.addLink(LinkAnnotation.Url(destination, TextLinkStyles(style = styles.link)), span.textStart, span.textEnd)
                }
            }
        }
    }
    return result.toAnnotatedString()
}

private fun String.safeLinkDestination(): String? =
    when {
        startsWith("https://", ignoreCase = true) ||
            startsWith("http://", ignoreCase = true) ||
            startsWith("mailto:", ignoreCase = true) ||
            startsWith("tel:", ignoreCase = true) -> this
        startsWith("www.", ignoreCase = true) -> "https://$this"
        contains('@') && !contains(':') && !contains(' ') -> "mailto:$this"
        else -> null
    }

private fun MarkdownPreviewStyles.forKind(kind: MarkdownStyleKind): SpanStyle =
    when (kind) {
        MarkdownStyleKind.HEADING_1 -> h1
        MarkdownStyleKind.HEADING_2 -> h2
        MarkdownStyleKind.HEADING_3 -> h3
        MarkdownStyleKind.HEADING_4 -> h4
        MarkdownStyleKind.HEADING_5 -> h5
        MarkdownStyleKind.HEADING_6 -> h6
        MarkdownStyleKind.STRONG -> strong
        MarkdownStyleKind.EMPHASIS -> emphasis
        MarkdownStyleKind.STRIKETHROUGH -> strikethrough
        MarkdownStyleKind.INLINE_CODE, MarkdownStyleKind.CODE_BLOCK -> code
        MarkdownStyleKind.LINK -> link
        MarkdownStyleKind.BLOCK_QUOTE -> quote
        MarkdownStyleKind.LIST_MARKER -> SpanStyle()
    }
