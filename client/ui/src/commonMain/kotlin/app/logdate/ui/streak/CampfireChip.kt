@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.streak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing

const val CAMPFIRE_CHIP_TAG = "campfire_chip"

/**
 * A compact pill with a small campfire and the current fire's day count, for app bars.
 */
@Composable
fun CampfireChip(
    presentation: CampfirePresentation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = campfireShortDescription(presentation)
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        modifier =
            modifier
                .semantics { contentDescription = description }
                .testTag(CAMPFIRE_CHIP_TAG),
    ) {
        Row(
            modifier =
                Modifier
                    .clearAndSetSemantics {}
                    .padding(
                        start = Spacing.sm,
                        end = if (presentation.runDays > 0) Spacing.md else Spacing.sm,
                        top = Spacing.xs,
                        bottom = Spacing.xs,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Campfire(
                phase = presentation.phase,
                size = presentation.size,
                waitingForToday = presentation.isWaitingForToday,
                modifier = Modifier.size(24.dp),
            )
            if (presentation.runDays > 0) {
                Text(
                    text = presentation.runDays.toString(),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
