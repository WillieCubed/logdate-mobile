@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import app.logdate.ui.theme.Spacing

/** One mutually exclusive preference. The whole row owns the radio-button action. */
@Composable
fun SettingsChoiceItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    description: String? = null,
    enabled: Boolean = LocalSettingsEnabled.current,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    SettingsRow(
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        modifier = Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        enabled = enabled,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                trailingContent?.invoke()
                RadioButton(selected = selected, onClick = null, enabled = enabled)
            }
        },
    )
}
