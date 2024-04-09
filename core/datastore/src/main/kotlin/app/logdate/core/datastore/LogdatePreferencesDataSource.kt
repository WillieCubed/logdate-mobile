package app.logdate.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.logdate.core.datastore.model.AppSecurityLevel
import app.logdate.core.datastore.model.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import javax.inject.Inject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * A local data source for user preferences.
 */
class LogdatePreferencesDataSource @Inject constructor(
    private val userPreferences: DataStore<Preferences>
) {

    // TODO: Migrate to datastore-proto
    companion object {
        val IS_ONBOARDED = booleanPreferencesKey("is_onboarded")
        val ONBOARDED_TIMESTAMP = longPreferencesKey("onboarded_timestamp")
        val SECURITY_LEVEL = stringPreferencesKey("security_level")
    }

    val userData: Flow<UserData> = userPreferences.data.map {
        UserData(
            isOnboarded = it[IS_ONBOARDED] ?: false,
            onboardedDate = Instant.fromEpochMilliseconds(it[ONBOARDED_TIMESTAMP] ?: 0),
            securityLevel = AppSecurityLevel.valueOf(
                it[SECURITY_LEVEL] ?: AppSecurityLevel.NONE.name
            ),
            favoriteNotes = emptyList(),
        )
    }

    suspend fun setShouldHideOnboarding(value: Boolean) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                putAll(
                    IS_ONBOARDED to value,
                    ONBOARDED_TIMESTAMP to System.currentTimeMillis(),
                )
            }
        }
    }

    suspend fun setShouldShowBiometric(value: Boolean) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                putAll(
                    SECURITY_LEVEL to if (value) AppSecurityLevel.BIOMETRIC.name else AppSecurityLevel.NONE.name
                )
            }
        }
    }
}