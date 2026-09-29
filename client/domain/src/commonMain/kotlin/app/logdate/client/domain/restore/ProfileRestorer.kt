package app.logdate.client.domain.restore

import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.shared.model.profile.LogDateProfile
import io.github.aakira.napier.Napier
import kotlin.time.Instant

/** Applies an archive's profile choices without replacing populated local choices during Cloud recovery. */
internal class ProfileRestorer(
    private val profileRepository: ProfileRepository,
) {
    suspend fun restore(
        profile: LogDateProfile?,
        options: RestoreOptions,
        warnings: MutableList<String>,
        onProgress: (suspend (RestoreProgressPhase) -> Unit)?,
    ) {
        if (profile == null) return

        onProgress?.invoke(RestoreProgressPhase.RESTORING_PROFILE)
        Napier.i("Restore: importing profile")
        val existing = profileRepository.getCurrentProfile()
        if (options.preservePopulatedLocalProfile) {
            fillUnpopulatedFields(existing, profile, warnings)
            return
        }

        val shouldWrite =
            if (options.strategy == RestoreStrategy.MERGE_KEEP_NEWEST && existing == LogDateProfile()) {
                profile != LogDateProfile()
            } else {
                shouldOverwrite(existing.lastUpdatedAt, profile.lastUpdatedAt, options.strategy)
            }
        if (shouldWrite) overwriteFields(existing, profile, warnings)
    }

    private suspend fun fillUnpopulatedFields(
        existing: LogDateProfile,
        profile: LogDateProfile,
        warnings: MutableList<String>,
    ) {
        if (existing.displayName.isBlank() && profile.displayName.isNotBlank()) {
            profileRepository
                .updateDisplayName(profile.displayName)
                .onFailure { warnings.add("Failed to restore profile display name: ${it.message ?: "unknown error"}") }
        }
        if (existing.birthday == null && profile.birthday != null) {
            profileRepository
                .updateBirthday(profile.birthday)
                .onFailure { warnings.add("Failed to restore profile birthday: ${it.message ?: "unknown error"}") }
        }
        if (existing.profilePhotoUri.isNullOrBlank() && !profile.profilePhotoUri.isNullOrBlank()) {
            profileRepository
                .updateProfilePhoto(profile.profilePhotoUri)
                .onFailure { warnings.add("Failed to restore profile photo: ${it.message ?: "unknown error"}") }
        }
        val bio = existing.bio.takeUnless { it.isNullOrBlank() } ?: profile.bio.takeUnless { it.isNullOrBlank() }
        val originalBio =
            existing.originalBio.takeUnless { it.isNullOrBlank() }
                ?: profile.originalBio.takeUnless { it.isNullOrBlank() }
        if (bio != existing.bio || originalBio != existing.originalBio) {
            profileRepository
                .updateBio(bio, originalBio)
                .onFailure { warnings.add("Failed to restore profile bio: ${it.message ?: "unknown error"}") }
        }
    }

    private suspend fun overwriteFields(
        existing: LogDateProfile,
        profile: LogDateProfile,
        warnings: MutableList<String>,
    ) {
        if (existing.displayName != profile.displayName) {
            profileRepository
                .updateDisplayName(profile.displayName)
                .onFailure { warnings.add("Failed to restore profile display name: ${it.message ?: "unknown error"}") }
        }
        if (existing.birthday != profile.birthday) {
            profileRepository
                .updateBirthday(profile.birthday)
                .onFailure { warnings.add("Failed to restore profile birthday: ${it.message ?: "unknown error"}") }
        }
        if (existing.profilePhotoUri != profile.profilePhotoUri) {
            profileRepository
                .updateProfilePhoto(profile.profilePhotoUri)
                .onFailure { warnings.add("Failed to restore profile photo: ${it.message ?: "unknown error"}") }
        }
        if (existing.bio != profile.bio || existing.originalBio != profile.originalBio) {
            profileRepository
                .updateBio(profile.bio, profile.originalBio)
                .onFailure { warnings.add("Failed to restore profile bio: ${it.message ?: "unknown error"}") }
        }
    }
}

internal fun shouldOverwrite(
    existing: Instant?,
    incoming: Instant,
    strategy: RestoreStrategy,
): Boolean =
    when (strategy) {
        RestoreStrategy.MERGE_KEEP_NEWEST -> existing == null || incoming > existing
        RestoreStrategy.REPLACE_EXISTING -> true
    }
