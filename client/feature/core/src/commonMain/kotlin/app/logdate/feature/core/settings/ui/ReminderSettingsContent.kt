@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import app.logdate.client.domain.reminders.ReminderPreferencesState
import app.logdate.ui.common.MasterFeatureToggle
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsFeatureGroup
import app.logdate.ui.common.SettingsNavigationItem
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SimpleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.phone_reminders_campfire_fix
import logdate.client.feature.core.generated.resources.phone_reminders_campfire_missing
import logdate.client.feature.core.generated.resources.phone_reminders_campfire_ready
import logdate.client.feature.core.generated.resources.phone_reminders_campfire_title
import logdate.client.feature.core.generated.resources.phone_reminders_daily_limit
import logdate.client.feature.core.generated.resources.phone_reminders_notifications_fix
import logdate.client.feature.core.generated.resources.phone_reminders_notifications_missing
import logdate.client.feature.core.generated.resources.phone_reminders_notifications_ready
import logdate.client.feature.core.generated.resources.phone_reminders_notifications_title
import logdate.client.feature.core.generated.resources.phone_reminders_places_fix
import logdate.client.feature.core.generated.resources.phone_reminders_places_missing
import logdate.client.feature.core.generated.resources.phone_reminders_places_ready
import logdate.client.feature.core.generated.resources.phone_reminders_places_title
import logdate.client.feature.core.generated.resources.phone_reminders_save_failed
import logdate.client.feature.core.generated.resources.phone_reminders_supplements_body
import logdate.client.feature.core.generated.resources.phone_reminders_supplements_title
import logdate.client.feature.core.generated.resources.phone_reminders_title
import logdate.client.feature.core.generated.resources.phone_reminders_wind_down_body
import logdate.client.feature.core.generated.resources.phone_reminders_wind_down_fix
import logdate.client.feature.core.generated.resources.phone_reminders_wind_down_title
import logdate.client.feature.core.generated.resources.phone_reminders_workout_title
import logdate.client.feature.core.generated.resources.phone_reminders_workout_unavailable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class ReminderSettingsFix { NOTIFICATIONS, PLACES, STREAKS, SLEEP }

@Composable
fun ReminderSettingsContent(
    state: ReminderPreferencesState,
    notificationsAllowed: Boolean,
    locationAllowed: Boolean,
    streaksEnabled: Boolean,
    busy: Boolean,
    saveFailed: Boolean,
    onEnabled: (Boolean) -> Unit,
    onFix: (ReminderSettingsFix) -> Unit,
    onBack: () -> Unit,
    locationHistoryEnabled: Boolean = false,
) {
    val placesReady = state.capturePlaces && locationAllowed && locationHistoryEnabled
    SettingsScaffold(
        title = stringResource(Res.string.phone_reminders_title),
        onBack = onBack,
        modifier = Modifier.testTag("reminder_settings"),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    text = stringResource(Res.string.phone_reminders_daily_limit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MasterFeatureToggle(
                    label = stringResource(Res.string.phone_reminders_title),
                    checked = state.enabled,
                    onCheckedChange = onEnabled,
                    enabled = state.ready && state.rolloutEnabled && !busy,
                )
            }
        }
        if (saveFailed) {
            item {
                Text(
                    stringResource(Res.string.phone_reminders_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }
        item {
            SettingsFeatureGroup(enabled = state.enabled && state.ready && state.rolloutEnabled && !busy) {
                MaterialContainer(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                    ReminderAvailabilityRow(
                        Res.string.phone_reminders_notifications_title,
                        if (notificationsAllowed) {
                            Res.string.phone_reminders_notifications_ready
                        } else {
                            Res.string.phone_reminders_notifications_missing
                        },
                        if (notificationsAllowed) null else Res.string.phone_reminders_notifications_fix,
                        icon = Icons.Rounded.NotificationsActive,
                    ) { onFix(ReminderSettingsFix.NOTIFICATIONS) }
                    ReminderAvailabilityRow(
                        Res.string.phone_reminders_places_title,
                        if (placesReady) Res.string.phone_reminders_places_ready else Res.string.phone_reminders_places_missing,
                        if (placesReady) null else Res.string.phone_reminders_places_fix,
                        icon = Icons.Rounded.Place,
                    ) { onFix(ReminderSettingsFix.PLACES) }
                    ReminderAvailabilityRow(
                        Res.string.phone_reminders_campfire_title,
                        if (streaksEnabled) Res.string.phone_reminders_campfire_ready else Res.string.phone_reminders_campfire_missing,
                        if (streaksEnabled) null else Res.string.phone_reminders_campfire_fix,
                        icon = Icons.Rounded.LocalFireDepartment,
                    ) { onFix(ReminderSettingsFix.STREAKS) }
                    ReminderAvailabilityRow(
                        Res.string.phone_reminders_wind_down_title,
                        Res.string.phone_reminders_wind_down_body,
                        Res.string.phone_reminders_wind_down_fix,
                        icon = Icons.Rounded.NightsStay,
                    ) { onFix(ReminderSettingsFix.SLEEP) }
                    ReminderAvailabilityRow(Res.string.phone_reminders_supplements_title, Res.string.phone_reminders_supplements_body)
                    ReminderAvailabilityRow(Res.string.phone_reminders_workout_title, Res.string.phone_reminders_workout_unavailable)
                }
            }
        }
    }
}

@Composable
private fun ReminderAvailabilityRow(
    title: StringResource,
    body: StringResource,
    fix: StringResource? = null,
    icon: ImageVector? = null,
    onFix: () -> Unit = {},
) {
    if (fix != null && icon != null) {
        SettingsNavigationItem(
            title = stringResource(title),
            description = stringResource(body),
            icon = { Icon(icon, contentDescription = null) },
            onClick = onFix,
            onClickLabel = stringResource(fix),
        )
    } else {
        SimpleSettingsItem(
            title = stringResource(title),
            description = stringResource(body),
            onClick = null,
            action = {},
        )
    }
}
