@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.quiet_save_description
import logdate.client.feature.core.generated.resources.quiet_save_preference_error
import logdate.client.feature.core.generated.resources.quiet_save_section_title
import logdate.client.feature.core.generated.resources.quiet_save_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun QuietSaveSettingsSection(
    state: VoiceNotesSettingsViewModel.UiState,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.quietSaveAvailable) return
    SettingsSection(title = stringResource(Res.string.quiet_save_section_title), modifier = modifier) {
        ToggleSettingsItem(
            title = stringResource(Res.string.quiet_save_title),
            description = stringResource(Res.string.quiet_save_description),
            checked = state.quietSaveEnabled,
            onCheckedChange = onEnabledChange,
        )
    }
    if (state.quietSaveWriteFailed) {
        Text(
            stringResource(Res.string.quiet_save_preference_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
