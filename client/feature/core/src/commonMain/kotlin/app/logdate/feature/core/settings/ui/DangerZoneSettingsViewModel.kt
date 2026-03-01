package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.clearAllLogDateTables
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.client.repository.user.UserStateRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DangerZoneSettingsViewModel(
    private val database: LogDateDatabase,
    private val preferencesDataSource: LogdatePreferencesDataSource,
    private val passkeyAccountRepository: PasskeyAccountRepository,
    private val userStateRepository: UserStateRepository,
) : ViewModel() {

    fun clearLocalData(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            clearLocalDataInternal()
            onComplete?.invoke()
        }
    }

    fun resetApp(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            Napier.i("DangerZone reset requested")

            clearLocalDataInternal()

            val prefsResult = preferencesDataSource.clearUserData()
            if (prefsResult.isFailure) {
                Napier.e("Failed to clear user preferences", prefsResult.exceptionOrNull())
            }

            passkeyAccountRepository.signOut()
            userStateRepository.setIsOnboardingComplete(false)

            Napier.i("DangerZone reset completed")
            onComplete?.invoke()
        }
    }

    private suspend fun clearLocalDataInternal() {
        val result = runCatching {
            withContext(Dispatchers.Default) {
                database.clearAllLogDateTables()
            }
        }

        if (result.isFailure) {
            Napier.e("Failed to clear local data", result.exceptionOrNull())
        } else {
            Napier.i("Local database tables cleared")
        }
    }
}
