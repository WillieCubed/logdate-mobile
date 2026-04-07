@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Renders a verbatim line the AI pulled from one of the user's actual journal entries.
 *
 * The point of this beat is recognition: the user reads their own sentence — typed in
 * a hurry on a Tuesday and forgotten about — back at them in big quiet type and goes
 * "huh, I wrote that, and it's true". So [text] is rendered prominent and readable, in
 * a serif face that signals "this is a quote not a caption". [whyItHits] sits beneath
 * in smaller italic type as the rewind's quiet reason for surfacing it.
 *
 * The background hue varies per quote via [accentSeed] so a stretch of quote panels
 * doesn't read as templated.
 */
@Composable
fun HighlightedQuotePanel(
    text: String,
    whyItHits: String,
    accentSeed: Int,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = quoteBackground(accentSeed)
    Box(
        modifier = modifier.fillMaxSize().background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.padding(horizontal = 40.dp, vertical = 80.dp),
        ) {
            Text(
                text = "\u201C${text}\u201D",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start,
                lineHeight = MaterialTheme.typography.headlineLarge.lineHeight,
            )
            Text(
                text = whyItHits,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f),
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Start,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
            )
        }
    }
}

/**
 * Picks a deep, quiet background color from a small palette so the user's words read as
 * the foreground rather than competing with chrome. The palette intentionally avoids
 * bright accents — the line itself is the loudest thing on the panel.
 */
private fun quoteBackground(seed: Int): Color {
    val palette =
        listOf(
            Color(0xFF14202E), // ink blue
            Color(0xFF231828), // plum
            Color(0xFF1B2A1F), // pine
            Color(0xFF2E1B1B), // cocoa
            Color(0xFF1F1F26), // charcoal
        )
    return palette[abs(seed) % palette.size]
}
