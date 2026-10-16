package app.logdate.client.data.profile

import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.shared.model.profile.LogDateProfile
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Offline-first implementation of ProfileRepository using DataStore for persistence.
 *
 * This repository manages local profile data that's always available for app personalization,
 * independent of cloud account connectivity.
 */
class OfflineFirstProfileRepository(
    private val preferencesDataSource: LogdatePreferencesDataSource,
    private val currentRecoveryScope: suspend () -> JournalMergeScope? = { null },
) : ProfileRepository {
    override suspend fun compareAndSetProfileForRestore(
        expected: LogDateProfile,
        replacement: LogDateProfile,
        scope: JournalMergeScope?,
        recoveryId: String,
    ): Boolean {
        val receipt = "${scope?.ownerId?.length}:${scope?.ownerId}${scope?.serverOrigin?.length}:${scope?.serverOrigin}:$recoveryId"
        return preferencesDataSource.archiveProfileRecoveryStore().compareAndSet(expected, replacement, receipt) {
            check(currentRecoveryScope() == scope) { "Profile recovery account changed" }
        }
    }

    override val currentProfile: Flow<LogDateProfile> =
        preferencesDataSource.userData.map { userData ->
            LogDateProfile(
                displayName = userData.displayName,
                birthday = userData.birthday.takeIf { it != Instant.DISTANT_PAST },
                profilePhotoUri = userData.profilePhotoUri,
                bio = userData.bio,
                originalBio = userData.originalBio,
                createdAt = userData.profileCreatedAt,
                lastUpdatedAt = userData.profileLastUpdatedAt,
            )
        }

    override suspend fun updateDisplayName(displayName: String): Result<LogDateProfile> =
        try {
            val now = Clock.System.now()
            val result = preferencesDataSource.updateDisplayName(displayName)

            result.fold(
                onSuccess = { userData ->
                    val updatedProfile =
                        LogDateProfile(
                            displayName = userData.displayName,
                            birthday = userData.birthday.takeIf { it != Instant.DISTANT_PAST },
                            profilePhotoUri = userData.profilePhotoUri,
                            bio = userData.bio,
                            originalBio = userData.originalBio,
                            createdAt = userData.profileCreatedAt,
                            lastUpdatedAt = now,
                        )

                    // Update the last updated timestamp
                    preferencesDataSource.updateProfileLastUpdated(now)

                    Napier.d("Display name updated successfully: $displayName")
                    Result.success(updatedProfile)
                },
                onFailure = { exception ->
                    Napier.e("Failed to update display name", exception)
                    Result.failure(exception)
                },
            )
        } catch (e: Exception) {
            Napier.e("Unexpected error updating display name", e)
            Result.failure(e)
        }

    override suspend fun updateBirthday(birthday: Instant?): Result<LogDateProfile> =
        try {
            val now = Clock.System.now()
            val result = preferencesDataSource.updateBirthday(birthday ?: Instant.DISTANT_PAST)

            result.fold(
                onSuccess = { userData ->
                    val updatedProfile =
                        LogDateProfile(
                            displayName = userData.displayName,
                            birthday = userData.birthday.takeIf { it != Instant.DISTANT_PAST },
                            profilePhotoUri = userData.profilePhotoUri,
                            bio = userData.bio,
                            originalBio = userData.originalBio,
                            createdAt = userData.profileCreatedAt,
                            lastUpdatedAt = now,
                        )

                    // Update the last updated timestamp
                    preferencesDataSource.updateProfileLastUpdated(now)

                    Napier.d("Birthday updated successfully")
                    Result.success(updatedProfile)
                },
                onFailure = { exception ->
                    Napier.e("Failed to update birthday", exception)
                    Result.failure(exception)
                },
            )
        } catch (e: Exception) {
            Napier.e("Unexpected error updating birthday", e)
            Result.failure(e)
        }

    override suspend fun updateProfilePhoto(profilePhotoUri: String?): Result<LogDateProfile> =
        try {
            val now = Clock.System.now()
            val result = preferencesDataSource.updateProfilePhotoUri(profilePhotoUri)

            result.fold(
                onSuccess = { userData ->
                    val updatedProfile =
                        LogDateProfile(
                            displayName = userData.displayName,
                            birthday = userData.birthday.takeIf { it != Instant.DISTANT_PAST },
                            profilePhotoUri = userData.profilePhotoUri,
                            bio = userData.bio,
                            originalBio = userData.originalBio,
                            createdAt = userData.profileCreatedAt,
                            lastUpdatedAt = now,
                        )

                    // Update the last updated timestamp
                    preferencesDataSource.updateProfileLastUpdated(now)

                    Napier.d("Profile photo updated successfully")
                    Result.success(updatedProfile)
                },
                onFailure = { exception ->
                    Napier.e("Failed to update profile photo", exception)
                    Result.failure(exception)
                },
            )
        } catch (e: Exception) {
            Napier.e("Unexpected error updating profile photo", e)
            Result.failure(e)
        }

    override suspend fun updateBio(
        bio: String?,
        originalBio: String?,
    ): Result<LogDateProfile> =
        try {
            val now = Clock.System.now()
            val result = preferencesDataSource.updateBio(bio, originalBio)

            result.fold(
                onSuccess = { userData ->
                    val updatedProfile =
                        LogDateProfile(
                            displayName = userData.displayName,
                            birthday = userData.birthday.takeIf { it != Instant.DISTANT_PAST },
                            profilePhotoUri = userData.profilePhotoUri,
                            bio = userData.bio,
                            originalBio = userData.originalBio,
                            createdAt = userData.profileCreatedAt,
                            lastUpdatedAt = now,
                        )

                    // Update the last updated timestamp
                    preferencesDataSource.updateProfileLastUpdated(now)

                    Napier.d("Bio updated successfully")
                    Result.success(updatedProfile)
                },
                onFailure = { exception ->
                    Napier.e("Failed to update bio", exception)
                    Result.failure(exception)
                },
            )
        } catch (e: Exception) {
            Napier.e("Unexpected error updating bio", e)
            Result.failure(e)
        }

    override suspend fun getCurrentProfile(): LogDateProfile =
        try {
            val userData = preferencesDataSource.getCurrentUserData()
            LogDateProfile(
                displayName = userData.displayName,
                birthday = userData.birthday.takeIf { it != Instant.DISTANT_PAST },
                profilePhotoUri = userData.profilePhotoUri,
                bio = userData.bio,
                originalBio = userData.originalBio,
                createdAt = userData.profileCreatedAt,
                lastUpdatedAt = userData.profileLastUpdatedAt,
            )
        } catch (e: Exception) {
            Napier.e("Failed to get current profile", e)
            // Return default profile on error
            LogDateProfile()
        }

    override suspend fun clearProfile(): Result<Unit> =
        try {
            preferencesDataSource.archiveProfileRecoveryStore().clearProfile()
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Unexpected error clearing profile", e)
            Result.failure(e)
        }
}
