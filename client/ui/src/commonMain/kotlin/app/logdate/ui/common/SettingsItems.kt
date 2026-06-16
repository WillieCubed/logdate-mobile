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
            modifier =
                Modifier
                    .padding(vertical = Spacing.sm)
                    .disabledAlpha(LocalSettingsEnabled.current),
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
 * @param enabled Whether the item is interactive; when false it grays out and ignores taps
 */
@Composable
fun SimpleSettingsItem(
    title: String,
    description: String,
    overline: String? = null,
    onClick: () -> Unit = {},
    enabled: Boolean = LocalSettingsEnabled.current,
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
        modifier =
            Modifier
                .fillMaxWidth()
                .disabledAlpha(enabled)
                .clickable(enabled = enabled) { onClick() },
    )
}

/**
 * A [SimpleSettingsItem] that has a toggleable switch.
 *
 * Tapping anywhere on the row toggles the switch. When [enabled] is false the row grays out and
 * stops responding — use this (not hiding) for a setting that depends on a feature's
 * [MasterFeatureToggle] being on. Defaults to the surrounding [LocalSettingsEnabled], so a
 * [SettingsFeatureGroup] disables it automatically.
 */
@Composable
fun ToggleSettingsItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    overline: String? = null,
    enabled: Boolean = LocalSettingsEnabled.current,
) {
    SimpleSettingsItem(
        title = title,
        description = description,
        overline = overline,
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        action = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
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
    enabled: Boolean = LocalSettingsEnabled.current,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).disabledAlpha(enabled),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(enabled = enabled, onClick = onNavigate)
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
                    .clickable(enabled = enabled) { onCheckedChange(!checked) }
                    .padding(horizontal = Spacing.lg)
                    .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

/**
 * The master on/off switch for an entire feature, styled as a prominent
 * `secondaryContainer` pill with `extraLarge` shape (the Pixel Settings detail-screen pattern).
 *
 * Use this **exactly once** per settings detail screen, as the switch that turns the whole feature
 * (and the group of settings below it) on or off — e.g. "Use recommendations". It is **not** a
 * generic switch: for an individual setting within a feature, use [ToggleSettingsItem] instead.
 *
 * When this master is off, keep the dependent settings **visible but disabled** rather than hiding
 * them — render them and pass `enabled = checked` so they gray out (see [ToggleSettingsItem],
 * [SettingsNavigationItem]). Reserve hiding for content that is genuinely unavailable, such as a
 * setting blocked behind a missing OS permission.
 *
 * @param label The label for the toggle
 * @param checked Whether the feature is currently enabled
 * @param onCheckedChange Callback when the toggle is changed
 * @param enabled Whether the master toggle itself is interactive
 * @param modifier Modifier to be applied to the outer surface
 */
@Composable
fun MasterFeatureToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.disabledAlpha(enabled),
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
    enabled: Boolean = LocalSettingsEnabled.current,
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
                .disabledAlpha(enabled)
                .clickable(enabled = enabled, onClick = onClick),
    )
}
