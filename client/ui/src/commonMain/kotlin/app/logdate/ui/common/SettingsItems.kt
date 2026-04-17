@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import app.logdate.ui.theme.Spacing

/**
 * A block of related settings items used to group options together.
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = Spacing.sm),
        )

        MaterialContainer {
            content()
        }
    }
}

/**
 * A simple settings item with a slot for title and description and optional actions.
 *
 * @param title The title of the settings item
 * @param description The description text for the settings item
 * @param overline Optional overline text for additional context or grouping
 * @param onClick Callback when the item is clicked
 */
@Composable
fun SimpleSettingsItem(
    title: String,
    description: String,
    overline: String? = null,
    onClick: () -> Unit = {},
    action: @Composable () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        overlineContent = overline?.let { { Text(it) } },
        trailingContent = action,
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    )
}

/**
 * A [SimpleSettingsItem] that has a toggleable switch.
 *
 * Tapping anywhere on the row toggles the switch.
 */
@Composable
fun ToggleSettingsItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    overline: String? = null,
) {
    SimpleSettingsItem(
        title = title,
        description = description,
        overline = overline,
        onClick = { onCheckedChange(!checked) },
        action = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

/**
 * A settings item with both a toggleable switch and a separate navigation action.
 *
 * The left portion navigates to a detail screen when tapped. A vertical divider separates
 * the label area from the independently tappable switch on the right, following the Android
 * system settings pattern.
 */
@Composable
fun LinkedToggleSettingsItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onNavigate: () -> Unit,
    overline: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(onClick = onNavigate)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (overline != null) {
                Text(
                    text = overline,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = Spacing.sm))
        Box(
            modifier =
                Modifier
                    .clickable { onCheckedChange(!checked) }
                    .padding(horizontal = Spacing.lg)
                    .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/**
 * A prominent toggle row styled as a colored pill, used as the primary control
 * on settings detail screens (e.g., "Use recommendations").
 *
 * Follows the Pixel Settings detail-screen pattern where the primary toggle
 * sits in a `secondaryContainer`-colored pill with `extraLarge` shape.
 *
 * @param label The label for the toggle
 * @param checked Whether the toggle is currently enabled
 * @param onCheckedChange Callback when the toggle is changed
 * @param modifier Modifier to be applied to the outer surface
 */
@Composable
fun PrimaryTogglePill(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.alpha(if (enabled) 1f else 0.6f),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

/**
 * A two-line settings row that navigates to a sub-screen on tap. Has a leading icon
 * slot, headline, supporting description, and a trailing chevron so the affordance
 * reads "tap to drill in" — the standard pattern for a settings hub.
 */
@Composable
fun SettingsNavigationItem(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = {
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = icon,
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
            )
        },
        modifier =
            Modifier
                .alpha(if (enabled) 1f else 0.6f)
                .clickable(enabled = enabled, onClick = onClick),
    )
}
