package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.logdate.client.datastore.LogdatePreferencesDataSource.Companion.BIO
import app.logdate.client.datastore.LogdatePreferencesDataSource.Companion.DISPLAY_NAME
import app.logdate.client.datastore.LogdatePreferencesDataSource.Companion.ORIGINAL_BIO
import app.logdate.client.datastore.LogdatePreferencesDataSource.Companion.PROFILE_CREATED_AT
import app.logdate.client.datastore.LogdatePreferencesDataSource.Companion.PROFILE_LAST_UPDATED_AT
import app.logdate.client.datastore.LogdatePreferencesDataSource.Companion.PROFILE_PHOTO_URI
import app.logdate.shared.model.user.UserData
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Instant

internal class ProfilePreferences(
    private val userPreferences: DataStore<Preferences>,
    private val userData: Flow<UserData>,
    private val setBirthdate: suspend (Instant) -> Unit,
) {
    /**
     * Updates the user's display name
     */
    suspend fun updateDisplayName(displayName: String): Result<UserData> =
        try {
            userPreferences.updateData { preferences ->
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

    /**
     * Updates the user's birthday
     */
    suspend fun updateBirthday(birthday: Instant): Result<UserData> =
        try {
            setBirthdate(birthday)
            val userData = userData.first()
            Result.success(userData)
        } catch (e: Exception) {
            Napier.e("Failed to update birthday", e)
            Result.failure(e)
        }

    /**
     * Updates the user's profile photo URI
     */
    suspend fun updateProfilePhotoUri(profilePhotoUri: String?): Result<UserData> =
        try {
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

    /**
     * Updates the user's bio and original bio
     */
    suspend fun updateBio(
        bio: String?,
        originalBio: String?,
    ): Result<UserData> =
        try {
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
}
