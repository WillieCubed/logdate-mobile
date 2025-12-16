package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.shared.model.user.AppSecurityLevel
import app.logdate.shared.model.user.UserData
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        val DAY_START_HOUR = intPreferencesKey("day_start_hour")
        val DAY_END_HOUR = intPreferencesKey("day_end_hour")
        
        // Profile keys
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val PROFILE_PHOTO_URI = stringPreferencesKey("profile_photo_uri")
        val BIO = stringPreferencesKey("bio")
        val ORIGINAL_BIO = stringPreferencesKey("original_bio")
        val PROFILE_CREATED_AT = longPreferencesKey("profile_created_at")
        val PROFILE_LAST_UPDATED_AT = longPreferencesKey("profile_last_updated_at")
    }

    val userData: Flow<UserData> = userPreferences.data.map { prefs ->
        val birthdayMillis = prefs[BIRTHDAY] ?: 0
        Napier.d("Read birthday from preferences: $birthdayMillis")
        
        UserData(
            birthday = Instant.fromEpochMilliseconds(birthdayMillis),
            isOnboarded = prefs[IS_ONBOARDED] == true,
            onboardedDate = Instant.fromEpochMilliseconds(prefs[ONBOARDED_TIMESTAMP] ?: 0),
            securityLevel = AppSecurityLevel.valueOf(
                prefs[SECURITY_LEVEL] ?: AppSecurityLevel.NONE.name
            ),
            favoriteNotes = emptyList(),
            
            // Profile data
            displayName = prefs[DISPLAY_NAME] ?: "",
            profilePhotoUri = prefs[PROFILE_PHOTO_URI],
            bio = prefs[BIO],
            originalBio = prefs[ORIGINAL_BIO],
            profileCreatedAt = Instant.fromEpochMilliseconds(prefs[PROFILE_CREATED_AT] ?: Clock.System.now().toEpochMilliseconds()),
            profileLastUpdatedAt = Instant.fromEpochMilliseconds(prefs[PROFILE_LAST_UPDATED_AT] ?: Clock.System.now().toEpochMilliseconds())
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
        Napier.d("Setting birthday to: $birthday")
        userPreferences.updateData { preferences ->
            val millisValue = birthday.toEpochMilliseconds()
            Napier.d("Birthday in milliseconds: $millisValue")
            
            preferences.toMutablePreferences().apply {
                putAll(
                    BIRTHDAY to millisValue
                )
                Napier.d("Birthday set in preferences: ${this[BIRTHDAY]}")
            }
        }
        Napier.d("Birthday update complete")
    }
    
    /**
     * Gets the user preferences
     */
    suspend fun getPreferences(): UserPreferences {
        // Using first() to get the first emission from the flow
        // This avoids collecting indefinitely
        return userPreferences.data.map { prefs ->
            UserPreferences(
                dayStartHour = prefs[DAY_START_HOUR],
                dayEndHour = prefs[DAY_END_HOUR]
            )
        }.first()
    }
    
    /**
     * Sets the day start and end hours
     */
    suspend fun setDayBounds(startHour: Int, endHour: Int) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                putAll(
                    DAY_START_HOUR to startHour,
                    DAY_END_HOUR to endHour
                )
            }
        }
    }
    
    /**
     * Updates the user's display name
     */
    suspend fun updateDisplayName(displayName: String): Result<UserData> {
        return try {
            val updatedPrefs = userPreferences.updateData { preferences ->
                preferences.toMutablePreferences().apply {
                    this[DISPLAY_NAME] = displayName
                    // Set profile created timestamp if not already set
                    if (this[PROFILE_CREATED_AT] == null) {
                        this[PROFILE_CREATED_AT] = Clock.System.now().toEpochMilliseconds()
                    }
                }
            }
            
            val userData = userData.first()
            Result.success(userData)
        } catch (e: Exception) {
            Napier.e("Failed to update display name", e)
            Result.failure(e)
        }
    }
    
    /**
     * Updates the user's birthday
     */
    suspend fun updateBirthday(birthday: Instant): Result<UserData> {
        return try {
            setBirthdate(birthday)
            val userData = userData.first()
            Result.success(userData)
        } catch (e: Exception) {
            Napier.e("Failed to update birthday", e)
            Result.failure(e)
        }
    }
    
    /**
     * Updates the user's profile photo URI
     */
    suspend fun updateProfilePhotoUri(profilePhotoUri: String?): Result<UserData> {
        return try {
            userPreferences.updateData { preferences ->
                preferences.toMutablePreferences().apply {
                    if (profilePhotoUri != null) {
                        this[PROFILE_PHOTO_URI] = profilePhotoUri
                    } else {
                        this.remove(PROFILE_PHOTO_URI)
                    }
                }
            }
            
            val userData = userData.first()
            Result.success(userData)
        } catch (e: Exception) {
            Napier.e("Failed to update profile photo URI", e)
            Result.failure(e)
        }
    }
    
    /**
     * Updates the user's bio and original bio
     */
    suspend fun updateBio(bio: String?, originalBio: String?): Result<UserData> {
        return try {
            userPreferences.updateData { preferences ->
                preferences.toMutablePreferences().apply {
                    if (bio != null) {
                        this[BIO] = bio
                    } else {
                        this.remove(BIO)
                    }
                    
                    if (originalBio != null) {
                        this[ORIGINAL_BIO] = originalBio
                    } else {
                        this.remove(ORIGINAL_BIO)
                    }
                }
            }
            
            val userData = userData.first()
            Result.success(userData)
        } catch (e: Exception) {
            Napier.e("Failed to update bio", e)
            Result.failure(e)
        }
    }
    
    /**
     * Updates the profile last updated timestamp
     */
    suspend fun updateProfileLastUpdated(timestamp: Instant) {
        userPreferences.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[PROFILE_LAST_UPDATED_AT] = timestamp.toEpochMilliseconds()
            }
        }
    }
    
    /**
     * Gets the current user data synchronously
     */
    suspend fun getCurrentUserData(): UserData {
        return userData.first()
    }
    
    /**
     * Clears all user data
     */
    suspend fun clearUserData(): Result<Unit> {
        return try {
            userPreferences.updateData { preferences ->
                preferences.toMutablePreferences().apply {
                    clear()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Failed to clear user data", e)
            Result.failure(e)
        }
    }
}