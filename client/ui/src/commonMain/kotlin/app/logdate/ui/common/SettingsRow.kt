@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared layout and typography for settings rows. Prefer the navigation, toggle or simple
 * wrappers for ordinary preferences; use these slots for independently actionable controls.
 * Titles use bodyLarge, descriptions bodyMedium and overlines labelSmall on every settings page.
 * Interaction belongs to the wrapper so a passive status never acquires a fake click target.
 */
@Composable
fun SettingsRow(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = LocalSettingsEnabled.current,
) {
    ListItem(
        headlineContent = { ProvideTextStyle(MaterialTheme.typography.bodyLarge, headlineContent) },
        supportingContent = supportingContent?.let { { ProvideTextStyle(MaterialTheme.typography.bodyMedium, it) } },
        overlineContent = overlineContent?.let { { ProvideTextStyle(MaterialTheme.typography.labelSmall, it) } },
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        modifier = modifier.fillMaxWidth().disabledAlpha(enabled),
    )
}
