@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package app.logdate.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing

/**
 * Shared info card used throughout onboarding to describe a feature.
 *
 * Presents an icon on the left with a title and description to its right. Used across the
 * Overview, Birthday, Location, Recommendations, and Day Boundaries screens.
 */
@Composable
internal fun OverviewItem(
    title: String,
    description: String,
    icon: @Composable () -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.Start),
    ) {
        InfoIcon {
            icon()
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.Top),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Circular icon badge used inside [OverviewItem] to give each card a consistent visual anchor.
 */
@Composable
fun InfoIcon(icon: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        icon()
    }
}
