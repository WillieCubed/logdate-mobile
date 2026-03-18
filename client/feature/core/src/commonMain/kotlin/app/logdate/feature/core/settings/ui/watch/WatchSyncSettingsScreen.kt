@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.watch

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.client.domain.watch.WatchSyncSettings
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.watch_sync_auto
import logdate.client.feature.core.generated.resources.watch_sync_auto_description
import logdate.client.feature.core.generated.resources.watch_sync_behavior
import logdate.client.feature.core.generated.resources.watch_sync_health_data
import logdate.client.feature.core.generated.resources.watch_sync_health_data_description
import logdate.client.feature.core.generated.resources.watch_sync_mood_checkins
import logdate.client.feature.core.generated.resources.watch_sync_mood_checkins_description
import logdate.client.feature.core.generated.resources.watch_sync_settings
import logdate.client.feature.core.generated.resources.watch_sync_text_entries
import logdate.client.feature.core.generated.resources.watch_sync_text_entries_description
import logdate.client.feature.core.generated.resources.watch_sync_voice_notes
import logdate.client.feature.core.generated.resources.watch_sync_voice_notes_description
import logdate.client.feature.core.generated.resources.watch_sync_what_syncs
import org.jetbrains.compose.resources.stringResource

/**
 * Detail screen for configuring what data syncs between phone and watch.
 */
@Composable
fun WatchSyncSettingsScreen(
    onBack: () -> Unit,
    viewModel: WatchSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val syncSettings by viewModel.syncSettings.collectAsState()

    WatchSyncSettingsContent(
        settings = syncSettings,
        onBack = onBack,
        onSetSyncVoiceNotes = viewModel::setSyncVoiceNotes,
        onSetSyncTextEntries = viewModel::setSyncTextEntries,
        onSetSyncMoodCheckIns = viewModel::setSyncMoodCheckIns,
        onSetSyncHealthData = viewModel::setSyncHealthData,
        onSetAutoSync = viewModel::setAutoSync,
        modifier = modifier,
    )
}

@Composable
fun WatchSyncSettingsContent(
    settings: WatchSyncSettings,
    onBack: () -> Unit,
    onSetSyncVoiceNotes: (Boolean) -> Unit,
    onSetSyncTextEntries: (Boolean) -> Unit,
    onSetSyncMoodCheckIns: (Boolean) -> Unit,
    onSetSyncHealthData: (Boolean) -> Unit,
    onSetAutoSync: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(Res.string.watch_sync_settings),
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            SettingsSection(
                title = stringResource(Res.string.watch_sync_what_syncs),
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
                ToggleSettingsItem(
                    title = stringResource(Res.string.watch_sync_voice_notes),
                    description = stringResource(Res.string.watch_sync_voice_notes_description),
                    checked = settings.syncVoiceNotes,
                    onCheckedChange = onSetSyncVoiceNotes,
                )
                ToggleSettingsItem(
                    title = stringResource(Res.string.watch_sync_text_entries),
                    description = stringResource(Res.string.watch_sync_text_entries_description),
                    checked = settings.syncTextEntries,
                    onCheckedChange = onSetSyncTextEntries,
                )
                ToggleSettingsItem(
                    title = stringResource(Res.string.watch_sync_mood_checkins),
                    description = stringResource(Res.string.watch_sync_mood_checkins_description),
                    checked = settings.syncMoodCheckIns,
                    onCheckedChange = onSetSyncMoodCheckIns,
                )
                ToggleSettingsItem(
                    title = stringResource(Res.string.watch_sync_health_data),
                    description = stringResource(Res.string.watch_sync_health_data_description),
                    checked = settings.syncHealthData,
                    onCheckedChange = onSetSyncHealthData,
                )
            }
        }

        item {
            SettingsSection(
                title = stringResource(Res.string.watch_sync_behavior),
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
                ToggleSettingsItem(
                    title = stringResource(Res.string.watch_sync_auto),
                    description = stringResource(Res.string.watch_sync_auto_description),
                    checked = settings.autoSync,
                    onCheckedChange = onSetAutoSync,
                )
            }
        }
    }
}
