package app.logdate.ui.search

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Default style applied to matched terms when [parseSnippetMarkers] is called without an explicit
 * style. Bold-only — pass a richer [SpanStyle] (e.g. with `background`/`color`) at the call site
 * to make matches pop visually.
 */
val DefaultMatchStyle: SpanStyle = SpanStyle(fontWeight = FontWeight.Bold)

/**
 * Parses FTS5 snippet markers (`[` and `]`) into an [AnnotatedString], applying [matchStyle]
 * to each matched term.
 *
 * FTS5's `snippet()` function wraps matched terms in configurable markers. LogDate uses
 * `[` and `]` as markers. Unmatched `[` or `]` characters (orphaned markers) are treated
 * as literal text.
 *
 * @param snippet The raw snippet text from FTS5 with `[` and `]` markers
 * @param matchStyle The [SpanStyle] to apply to matched terms. Defaults to [DefaultMatchStyle]
 *   (bold-only) for backward compatibility.
 */
fun parseSnippetMarkers(
    snippet: String,
    matchStyle: SpanStyle = DefaultMatchStyle,
): AnnotatedString {
    if (!snippet.contains('[')) {
        return AnnotatedString(snippet)
    }

    return buildAnnotatedString {
        var i = 0
        while (i < snippet.length) {
            val openBracket = snippet.indexOf('[', i)
            if (openBracket == -1) {
                append(snippet.substring(i))
                break
            }

            val closeBracket = snippet.indexOf(']', openBracket + 1)
            if (closeBracket == -1) {
                append(snippet.substring(i))
                break
            }

            if (openBracket > i) {
                append(snippet.substring(i, openBracket))
            }

            val matchedText = snippet.substring(openBracket + 1, closeBracket)
            withStyle(matchStyle) {
                append(matchedText)
            }

            i = closeBracket + 1
        }
    }
}
