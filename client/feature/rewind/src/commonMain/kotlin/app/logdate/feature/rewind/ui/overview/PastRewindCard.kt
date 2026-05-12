@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.feature.rewind.ui.RewindOpenCallback
import app.logdate.ui.platform.PlatformIcons

/**
 * One row in the rewind overview history list.
 *
 * Two visual treatments:
 *  - **Weekly cadence rewinds** render with the standard secondary-container palette
 *    and the basic title text — the existing default.
 *  - **Milestone-detected rewinds** render in the tertiary-container palette with
 *    an asymmetric rounded shape, a small leading place icon, and the milestone
 *    summary stacked above the rewind title. The shape and color difference is the
 *    affordance — the user can tell at a glance that this card represents a specific
 *    event in their life rather than another normal weekly recap.
 */
@Composable
fun PastRewindCard(
    history: RewindHistoryUiState,
    onOpenRewind: RewindOpenCallback,
    modifier: Modifier = Modifier.Companion,
) {
    val milestone = history.milestone
    if (milestone != null) {
        MilestoneRewindCard(
            history = history,
            milestone = milestone,
            onOpenRewind = onOpenRewind,
            modifier = modifier,
        )
    } else {
        Surface(
            onClick = { onOpenRewind(history.uid) },
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = history.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                )
                Text(
                    text = history.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (history.message.isNotBlank()) {
                    Text(
                        text = history.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MilestoneRewindCard(
    history: RewindHistoryUiState,
    milestone: MilestoneSummaryUiState,
    onOpenRewind: RewindOpenCallback,
    modifier: Modifier = Modifier.Companion,
) {
    Surface(
        onClick = { onOpenRewind(history.uid) },
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        // Asymmetric corners — distinct from the symmetric weekly card.
        shape =
            RoundedCornerShape(
                topStart = 28.dp,
                topEnd = 8.dp,
                bottomStart = 8.dp,
                bottomEnd = 28.dp,
            ),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                painter =
                    when (milestone.kind) {
                        MilestoneKindUiState.LOCATION_CHANGE -> PlatformIcons.location()
                    },
                contentDescription = null,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = milestone.summary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = history.title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
