package app.logdate.ui.search

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Parses FTS5 snippet markers (`[` and `]`) into an [AnnotatedString] with bold spans
 * for matched terms.
 *
 * FTS5's `snippet()` function wraps matched terms in configurable markers. LogDate uses
 * `[` and `]` as markers. This function converts those markers into [FontWeight.Bold] spans.
 *
 * Unmatched `[` or `]` characters (orphaned markers) are treated as literal text.
 *
 * @param snippet The raw snippet text from FTS5 with `[` and `]` markers
 * @return An [AnnotatedString] with bold spans for matched portions
 */
fun parseSnippetMarkers(snippet: String): AnnotatedString {
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
                // Orphaned '[' — treat as literal
                append(snippet.substring(i))
                break
            }

            // Append text before the marker
            if (openBracket > i) {
                append(snippet.substring(i, openBracket))
            }

            // Append matched text with bold style
            val matchedText = snippet.substring(openBracket + 1, closeBracket)
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(matchedText)
            }

            i = closeBracket + 1
        }
    }
}
