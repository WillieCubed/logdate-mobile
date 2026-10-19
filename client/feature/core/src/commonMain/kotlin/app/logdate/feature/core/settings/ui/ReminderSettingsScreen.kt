@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.logdate.client.domain.reminders.ReminderPreferences
import app.logdate.client.domain.reminders.ReminderPreferencesState
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.repository.streak.StreakSettingsRepository
import app.logdate.ui.common.AlertDialog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.phone_reminders_places_consent_body
import logdate.client.feature.core.generated.resources.phone_reminders_places_consent_confirm
import logdate.client.feature.core.generated.resources.phone_reminders_places_consent_title
import logdate.client.feature.core.generated.resources.phone_reminders_save_failed
import logdate.client.ui.generated.resources.common_cancel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import logdate.client.ui.generated.resources.Res as UiRes

@Composable
internal fun ReminderSettingsScreen(
    onBack: () -> Unit,
    onFix: (ReminderSettingsFix) -> Unit,
) {
    val preferences = koinInject<ReminderPreferences>()
    val streaks = koinInject<StreakSettingsRepository>()
    val locations = koinInject<LocationTrackingSettingsRepository>()
    val state by remember(preferences) { preferences.observe() }.collectAsState(ReminderPreferencesState())
    val streaksEnabled by remember(streaks) { streaks.observeStreakEnabled() }.collectAsState(false)
    val locationSettings by remember(locations) { locations.observeSettings() }.collectAsState(LocationTrackingSettings())
    val access = rememberReminderSettingsAccess()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var placesConsent by remember { mutableStateOf(false) }

    fun save(
        operation: suspend () -> Boolean,
        after: () -> Unit = {},
    ) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try {
                if (operation()) after() else failed = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Napier.w("Could not save reminder preference", failure)
                failed = true
            } finally {
                busy = false
            }
        }
    }

    fun setUpPlaces() {
        if (!access.locationAllowed) access.resolveLocation() else onFix(ReminderSettingsFix.PLACES)
    }
    ReminderSettingsContent(
        state = state,
        notificationsAllowed = access.notificationsAllowed,
        locationAllowed = access.locationAllowed,
        streaksEnabled = streaksEnabled,
        busy = busy,
        saveFailed = failed,
        onEnabled = { enabled -> save({ preferences.setEnabled(enabled) }) },
        onFix = { fix ->
            when (fix) {
                ReminderSettingsFix.NOTIFICATIONS -> access.resolveNotifications()
                ReminderSettingsFix.PLACES -> if (!state.capturePlaces) placesConsent = true else setUpPlaces()
                else -> onFix(fix)
            }
        },
        onBack = onBack,
        locationHistoryEnabled = locationSettings.backgroundTrackingEnabled,
    )
    if (placesConsent) {
        AlertDialog(
            onDismissRequest = { if (!busy) placesConsent = false },
            title = { Text(stringResource(Res.string.phone_reminders_places_consent_title)) },
            text = {
                Column {
                    Text(stringResource(Res.string.phone_reminders_places_consent_body))
                    if (failed) Text(stringResource(Res.string.phone_reminders_save_failed), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    save({ preferences.setPlacesEnabled(true) }) {
                        placesConsent = false
                        setUpPlaces()
                    }
                }) { Text(stringResource(Res.string.phone_reminders_places_consent_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { placesConsent = false }) { Text(stringResource(UiRes.string.common_cancel)) }
            },
        )
    }
}
