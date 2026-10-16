package app.logdate.client.device.restore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey

internal suspend fun resetRestoredInstallationState(preferences: DataStore<Preferences>) {
    preferences.edit { restored -> restored.remove(stringPreferencesKey("device.id")) }
}
