package app.logdate.client.device.storage

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SessionStorage backed by SecureStorage for token persistence.
 */
class SecureSessionStorage(
    private val secureStorage: SecureStorage,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : SessionStorage {
    private object StorageKeys {
        const val ACCESS_TOKEN = "session_access_token"
        const val REFRESH_TOKEN = "session_refresh_token"
        const val ACCOUNT_ID = "session_account_id"
    }

    private val sessionState = MutableStateFlow<UserSession?>(null)

    init {
        scope.launch {
            sessionState.value = loadSession()
        }
    }

    override fun getSession(): UserSession? = sessionState.value

    override fun getSessionFlow() = sessionState.asStateFlow()

    override suspend fun hasValidSession(): Boolean {
        if (sessionState.value != null) {
            return true
        }
        return loadSession() != null
    }

    override fun saveSession(session: UserSession) {
        sessionState.value = session
        scope.launch {
            runCatching {
                secureStorage.putString(StorageKeys.ACCESS_TOKEN, session.accessToken)
                secureStorage.putString(StorageKeys.REFRESH_TOKEN, session.refreshToken)
                secureStorage.putString(StorageKeys.ACCOUNT_ID, session.accountId)
            }.onFailure { error ->
                Napier.e("Failed to persist secure session", error)
            }
        }
    }

    override fun clearSession() {
        sessionState.value = null
        scope.launch {
            runCatching {
                secureStorage.remove(StorageKeys.ACCESS_TOKEN)
                secureStorage.remove(StorageKeys.REFRESH_TOKEN)
                secureStorage.remove(StorageKeys.ACCOUNT_ID)
            }.onFailure { error ->
                Napier.e("Failed to clear secure session", error)
            }
        }
    }

    private suspend fun loadSession(): UserSession? =
        runCatching {
            val accessToken = secureStorage.getString(StorageKeys.ACCESS_TOKEN)
            val refreshToken = secureStorage.getString(StorageKeys.REFRESH_TOKEN)
            val accountId = secureStorage.getString(StorageKeys.ACCOUNT_ID)

            if (accessToken != null && refreshToken != null && accountId != null) {
                UserSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    accountId = accountId,
                )
            } else {
                null
            }
        }.getOrElse { error ->
            Napier.e("Failed to load secure session", error)
            null
        }
}
