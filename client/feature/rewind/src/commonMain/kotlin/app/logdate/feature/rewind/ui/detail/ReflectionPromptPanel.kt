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
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.feature.rewind.generated.resources.reflection_prompt_response_prefix
import org.jetbrains.compose.resources.stringResource

/**
 * Renders an AI-invented "noticing" prompt as a quiet two-line panel.
 *
 * The [observation] grounds the prompt in something specific from the user's actual week —
 * a count, a recurrence, a contrast. It's drawn small and italic so it reads as the rewind
 * "noticing", not as a wellness banner. The [invitation] sits beneath in larger type as the
 * actual question the user is being asked to consider.
 *
 * If the user has already typed a reply (and hasn't disabled reply chrome), it appears as
 * a quiet third row beneath the invitation so re-opening an old rewind shows the previous
 * answer at a glance.
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
    existingResponse: String? = null,
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
            if (existingResponse != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(Res.string.reflection_prompt_response_prefix),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                    Text(
                        text = existingResponse,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.85f),
                        fontStyle = FontStyle.Italic,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                    )
                }
            }
        }
    }
}
