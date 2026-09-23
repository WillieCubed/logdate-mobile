package app.logdate.feature.core.settings.ui

import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.clearAllLogDateTables
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.client.repository.user.UserStateRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Returns this device to a fresh install: the journal, preferences and onboarding progress are
 * cleared and the account is signed out. Each part is attempted even when another fails, and every
 * failure is logged.
 */
class DeviceEraser(
    private val database: LogDateDatabase,
    private val preferencesDataSource: LogdatePreferencesDataSource,
    private val passkeyAccountRepository: PasskeyAccountRepository,
    private val userStateRepository: UserStateRepository,
    private val onboardingStateResetter: OnboardingStateResetter,
) {
    suspend fun eraseEverything() {
        coroutineScope {
            launch { clearJournal() }
            launch {
                preferencesDataSource
                    .clearUserData()
                    .onFailure { Napier.e("Failed to clear user preferences", it) }
            }
            launch {
                runCatching { onboardingStateResetter.clear() }
                    .onFailure { Napier.e("Failed to clear onboarding device state", it) }
            }
            launch { passkeyAccountRepository.signOut() }
            launch { userStateRepository.setIsOnboardingComplete(false) }
        }
        Napier.i("Device erased")
    }

    /** Clears every journal table on this device. */
    suspend fun clearJournal(): Result<Unit> =
        runCatching { withContext(Dispatchers.Default) { database.clearAllLogDateTables() } }
            .onSuccess { Napier.i("Local database tables cleared") }
            .onFailure { Napier.e("Failed to clear local data", it) }
}
