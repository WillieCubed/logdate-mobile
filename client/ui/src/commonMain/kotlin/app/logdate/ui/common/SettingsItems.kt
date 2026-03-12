@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        supportingContent = { Text(description) },
        overlineContent = overline?.let { { Text(it) } },
        trailingContent = action,
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    )
}

/**
 * A [SimpleSettingsItem] that has a toggleable switch.
 */
@Composable
fun ToggleSettingsItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    overline: String? = null,
    onClick: () -> Unit = {},
) {
    SimpleSettingsItem(
        title = title,
        description = description,
        overline = overline,
        onClick = onClick,
        action = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
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
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
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
            )
        }
    }
}
