@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.rewind.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.logdate.feature.rewind.ui.overview.RewindPreviewUiState
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.theme.Spacing
import app.logdate.util.formatDateLocalized
import app.logdate.util.toReadableDateShort
import logdate.client.feature.rewind.generated.resources.*
import logdate.client.feature.rewind.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * Content layout for a rewind card with dates at the top and title at the bottom.
 *
 * Three visual states:
 * - **Unviewed**: Bold title, primary label, and a small "NEW" pill badge
 * - **Viewed**: Bold title, primary label, no badge
 * - **Pending**: Medium-weight title, muted label with a status dot
 *
 * @param rewind The rewind data to display
 * @param modifier Modifier for customizing the content container
 */
@Composable
fun RewindCoverCard(
    rewind: RewindPreviewUiState,
    modifier: Modifier = Modifier,
) {
    val isNew = rewind.rewindAvailable && !rewind.isViewed

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

        // "NEW" badge at top end for unviewed rewinds
        if (isNew) {
            NewRewindBadge(modifier = Modifier.align(Alignment.TopEnd))
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

            if (rewind.rewindAvailable && hasMetadata(rewind)) {
                RewindStatChipsRow(rewind = rewind)
            }

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

/**
 * Returns true when the rewind has at least one piece of metadata worth rendering
 * as a chip (entries, photos, people, or a primary location).
 *
 * When false, the chips row is omitted entirely to avoid a distracting empty slot.
 */
private fun hasMetadata(rewind: RewindPreviewUiState): Boolean =
    rewind.entryCount > 0 || rewind.photoCount > 0 || rewind.peopleCount > 0 || rewind.primaryLocation != null

/**
 * A row of small icon+count indicators showing at-a-glance metadata for a rewind.
 *
 * Uses subtle, secondary-emphasis styling so the chips inform without competing
 * with the title or message.
 */
@Composable
private fun RewindStatChipsRow(
    rewind: RewindPreviewUiState,
    modifier: Modifier = Modifier,
) {
    val chipColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (rewind.entryCount > 0) {
            RewindStatChip(label = "${rewind.entryCount}", color = chipColor) {
                Icon(painter = PlatformIcons.note(), contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
        if (rewind.photoCount > 0) {
            RewindStatChip(label = "${rewind.photoCount}", color = chipColor) {
                Icon(painter = PlatformIcons.camera(), contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
        if (rewind.peopleCount > 0) {
            RewindStatChip(label = "${rewind.peopleCount}", color = chipColor) {
                Icon(painter = PlatformIcons.people(), contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
        if (rewind.primaryLocation != null) {
            RewindStatChip(label = rewind.primaryLocation, color = chipColor) {
                Icon(painter = PlatformIcons.location(), contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/**
 * A single icon+label pair rendered inline for the metadata chip row.
 *
 * Deliberately unbordered and low-contrast — this is a glance-level affordance,
 * not a tap target. For a standalone, bordered chip see
 * [app.logdate.ui.common.MetadataChip].
 */
@Composable
private fun RewindStatChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides color,
        ) {
            icon()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/**
 * A small pill badge that signals an unviewed rewind.
 *
 * Uses the tertiary color family to stand out against the primary-container card
 * background while staying within the Material 3 palette.
 */
@Composable
private fun NewRewindBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(Res.string.badge_new),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
