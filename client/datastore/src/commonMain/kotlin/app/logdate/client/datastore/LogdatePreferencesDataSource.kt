package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.shared.model.user.AppSecurityLevel
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * A local data source for user preferences.
 */
class LogdatePreferencesDataSource(
    private val userPreferences: DataStore<Preferences>,
) {

    // TODO: Migrate to datastore-proto
    companion object {
        val BIRTHDAY = longPreferencesKey("birthday")
        val IS_ONBOARDED = booleanPreferencesKey("is_onboarded")
        val ONBOARDED_TIMESTAMP = longPreferencesKey("onboarded_timestamp")
        val SECURITY_LEVEL = stringPreferencesKey("security_level")
    }

    val userData: Flow<UserData> = userPreferences.data.map {
        UserData(
            birthday = Instant.fromEpochMilliseconds(it[BIRTHDAY] ?: 0),
            isOnboarded = it[IS_ONBOARDED] == true,
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
                    ONBOARDED_TIMESTAMP to Clock.System.now().toEpochMilliseconds(),
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

    suspend fun setBirthdate(birthday: Instant) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                putAll(BIRTHDAY to birthday.toEpochMilliseconds())
            }
        }
    }
}