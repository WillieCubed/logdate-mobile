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

/**
 * Renders an AI-invented "noticing" prompt as a quiet two-line panel.
 *
 * The [observation] grounds the prompt in something specific from the user's actual week —
 * a count, a recurrence, a contrast. It's drawn small and italic so it reads as the rewind
 * "noticing", not as a wellness banner. The [invitation] sits beneath in larger type as the
 * actual question the user is being asked to consider.
 *
 * The background hue varies per prompt via [accentSeed] so a sequence of prompt panels
 * doesn't read as templated.
 */
@Composable
fun ReflectionPromptPanel(
    observation: String,
    invitation: String,
    accentSeed: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().background(panelAccentBackground(accentSeed)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(horizontal = 40.dp, vertical = 64.dp),
        ) {
            Text(
                text = observation,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.7f),
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Start,
                lineHeight = MaterialTheme.typography.titleSmall.lineHeight,
            )
            Text(
                text = invitation,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start,
                lineHeight = MaterialTheme.typography.headlineMedium.lineHeight,
            )
        }
    }
}
