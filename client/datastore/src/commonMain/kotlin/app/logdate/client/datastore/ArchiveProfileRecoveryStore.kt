package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.logdate.client.datastore.LogdatePreferencesDataSource.Companion.BIO
import app.logdate.client.datastore.LogdatePreferencesDataSource.Companion.BIRTHDAY
import app.logdate.client.datastore.LogdatePreferencesDataSource.Companion.DISPLAY_NAME
import app.logdate.client.datastore.LogdatePreferencesDataSource.Companion.ORIGINAL_BIO
import app.logdate.client.datastore.LogdatePreferencesDataSource.Companion.PROFILE_CREATED_AT
import app.logdate.client.datastore.LogdatePreferencesDataSource.Companion.PROFILE_LAST_UPDATED_AT
import app.logdate.client.datastore.LogdatePreferencesDataSource.Companion.PROFILE_PHOTO_URI
import app.logdate.shared.model.profile.LogDateProfile
import app.logdate.shared.model.profile.asBirthdayDateInstant
import kotlin.time.Clock
import kotlin.time.Instant

/** Profile choices and their application receipt commit in one preferences transaction. */
class ArchiveProfileRecoveryStore(
    private val store: DataStore<Preferences>,
) {
    private val receipts = stringSetPreferencesKey("archive_profile_applied_recoveries")

    suspend fun compareAndSet(
        expected: LogDateProfile,
        replacement: LogDateProfile,
        receipt: String,
        requireScope: suspend () -> Unit,
    ): Boolean {
        var applied = false
        store.updateData { current ->
            requireScope()
            val previousReceipts = current[receipts].orEmpty()
            if (receipt in previousReceipts) return@updateData current
            val updated = current.toMutablePreferences()
            if (current.profile() == expected) {
                updated[DISPLAY_NAME] = replacement.displayName
                replacement.birthday?.let { updated[BIRTHDAY] = it.asBirthdayDateInstant().toEpochMilliseconds() }
                    ?: updated.remove(BIRTHDAY)
                replacement.profilePhotoUri?.let { updated[PROFILE_PHOTO_URI] = it } ?: updated.remove(PROFILE_PHOTO_URI)
                replacement.bio?.let { updated[BIO] = it } ?: updated.remove(BIO)
                replacement.originalBio?.let { updated[ORIGINAL_BIO] = it } ?: updated.remove(ORIGINAL_BIO)
                updated[PROFILE_CREATED_AT] = replacement.createdAt.toEpochMilliseconds()
                updated[PROFILE_LAST_UPDATED_AT] = replacement.lastUpdatedAt.toEpochMilliseconds()
                applied = true
            }
            updated[receipts] = previousReceipts + receipt
            requireScope()
            updated
        }
        return applied
    }

    suspend fun clearProfile() {
        store.updateData { current ->
            current.toMutablePreferences().apply {
                remove(DISPLAY_NAME)
                remove(BIRTHDAY)
                remove(PROFILE_PHOTO_URI)
                remove(BIO)
                remove(ORIGINAL_BIO)
                remove(PROFILE_CREATED_AT)
                this[PROFILE_LAST_UPDATED_AT] = Clock.System.now().toEpochMilliseconds()
            }
        }
    }

    private fun Preferences.profile() =
        LogDateProfile(
            displayName = this[DISPLAY_NAME].orEmpty(),
            birthday =
                this[BIRTHDAY]
                    ?.let(
                        Instant::fromEpochMilliseconds,
                    )?.takeUnless { it == Instant.DISTANT_PAST }
                    ?.asBirthdayDateInstant(),
            profilePhotoUri = this[PROFILE_PHOTO_URI],
            bio = this[BIO],
            originalBio = this[ORIGINAL_BIO],
            createdAt = this[PROFILE_CREATED_AT]?.takeIf { it != 0L }?.let(Instant::fromEpochMilliseconds) ?: Instant.DISTANT_PAST,
            lastUpdatedAt = this[PROFILE_LAST_UPDATED_AT]?.takeIf { it != 0L }?.let(Instant::fromEpochMilliseconds) ?: Instant.DISTANT_PAST,
        )
}
