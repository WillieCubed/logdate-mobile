@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui.watch.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.SimpleSettingsItem
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.watch_capture_mood
import logdate.client.feature.core.generated.resources.watch_capture_mood_description
import logdate.client.feature.core.generated.resources.watch_capture_places
import logdate.client.feature.core.generated.resources.watch_capture_places_description
import logdate.client.feature.core.generated.resources.watch_capture_section
import logdate.client.feature.core.generated.resources.watch_library_days
import logdate.client.feature.core.generated.resources.watch_library_days_cancel
import logdate.client.feature.core.generated.resources.watch_library_days_value
import logdate.client.feature.core.generated.resources.watch_library_on_this_day
import logdate.client.feature.core.generated.resources.watch_library_on_this_day_description
import logdate.client.feature.core.generated.resources.watch_library_rewind
import logdate.client.feature.core.generated.resources.watch_library_rewind_description
import logdate.client.feature.core.generated.resources.watch_library_section
import logdate.client.feature.core.generated.resources.watch_preferences_loading
import logdate.client.feature.core.generated.resources.watch_preferences_save_failed
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WatchCompanionSections(
    state: WatchCompanionPreferences?,
    saving: Boolean,
    saveFailed: Boolean,
    onDays: (Int) -> Unit,
    onThisDay: (Boolean) -> Unit,
    onRewind: (Boolean) -> Unit,
    onPlaces: (Boolean) -> Unit,
    onMood: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        if (saveFailed) {
            Text(stringResource(Res.string.watch_preferences_save_failed), color = MaterialTheme.colorScheme.error)
        }
        if (state == null) {
            Text(stringResource(Res.string.watch_preferences_loading))
            return@Column
        }
        SettingsSection(title = stringResource(Res.string.watch_library_section)) {
            WatchLibraryDaysItem(state.days, !saving, onDays)
            ToggleSettingsItem(
                title = stringResource(Res.string.watch_library_on_this_day),
                description = stringResource(Res.string.watch_library_on_this_day_description),
                checked = state.onThisDay,
                onCheckedChange = onThisDay,
                enabled = !saving,
            )
            ToggleSettingsItem(
                title = stringResource(Res.string.watch_library_rewind),
                description = stringResource(Res.string.watch_library_rewind_description),
                checked = state.rewind,
                onCheckedChange = onRewind,
                enabled = !saving,
            )
        }
        SettingsSection(title = stringResource(Res.string.watch_capture_section)) {
            ToggleSettingsItem(
                title = stringResource(Res.string.watch_capture_places),
                description = stringResource(Res.string.watch_capture_places_description),
                checked = state.places,
                onCheckedChange = onPlaces,
                enabled = !saving,
            )
            ToggleSettingsItem(
                title = stringResource(Res.string.watch_capture_mood),
                description = stringResource(Res.string.watch_capture_mood_description),
                checked = state.moodAfterRecording,
                onCheckedChange = onMood,
                enabled = !saving,
            )
        }
    }
}

@Composable
private fun WatchLibraryDaysItem(
    days: Int,
    enabled: Boolean,
    onDays: (Int) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    SimpleSettingsItem(
        title = stringResource(Res.string.watch_library_days),
        description = stringResource(Res.string.watch_library_days_value, days),
        onClick = { choosing = true },
        enabled = enabled,
        action = { Icon(Icons.Default.ExpandMore, contentDescription = null) },
    )
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(Res.string.watch_library_days)) },
            text = {
                Column {
                    SharedPreferenceKey.WINDOW_DAY_CHOICES.sorted().forEach { choice ->
                        TextButton(
                            onClick = {
                                choosing = false
                                onDays(choice)
                            },
                            enabled = enabled,
                        ) { Text(stringResource(Res.string.watch_library_days_value, choice)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { choosing = false }) { Text(stringResource(Res.string.watch_library_days_cancel)) }
            },
        )
    }
}
