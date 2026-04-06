@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.rewind.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.logdate.feature.rewind.ui.overview.RewindPreviewUiState
import app.logdate.ui.theme.Spacing
import app.logdate.util.formatDateLocalized
import app.logdate.util.toReadableDateShort
import logdate.client.feature.rewind.generated.resources.*
import logdate.client.feature.rewind.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * Content layout for a rewind card with dates at the top and title at the bottom.
 *
 * @param rewind The rewind data to display
 * @param modifier Modifier for customizing the content container
 */
@Composable
fun RewindCoverCard(
    rewind: RewindPreviewUiState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // Date range at top start
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            val dateColor =
                if (rewind.rewindAvailable) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            Text(
                text = rewind.start.toReadableDateShort(),
                style = MaterialTheme.typography.bodyMedium,
                color = dateColor,
            )
            Text(
                text = stringResource(Res.string.text_3),
                style = MaterialTheme.typography.bodyMedium,
                color = dateColor,
            )
            Text(
                text = formatDateLocalized(rewind.end),
                style = MaterialTheme.typography.bodyMedium,
                color = dateColor,
            )
        }

        // Title and subtitle at bottom start
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            // Label row
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = rewind.label,
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        if (rewind.rewindAvailable) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                )
                if (!rewind.rewindAvailable) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(6.dp),
                    ) {}
                }
            }

            // Title
            Text(
                text = rewind.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = if (rewind.rewindAvailable) FontWeight.Bold else FontWeight.Medium,
                color =
                    if (rewind.rewindAvailable) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )

            // Message/subtitle
            Text(
                text = rewind.message,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (rewind.rewindAvailable) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}
