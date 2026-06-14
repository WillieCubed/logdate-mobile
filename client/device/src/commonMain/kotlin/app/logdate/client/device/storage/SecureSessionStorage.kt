package app.logdate.client.device.storage

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.shared.config.LogDateConfigRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.text.encodeToByteArray

/**
 * SessionStorage backed by SecureStorage for token persistence.
 */
class SecureSessionStorage(
    private val secureStorage: SecureStorage,
    private val configRepository: LogDateConfigRepository,
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
            configRepository.backendUrl.collect { backendUrl ->
                sessionState.value = loadSession(backendUrl)
            }
        }
    }

    override fun getSession(): UserSession? = sessionState.value

    override fun getSessionFlow() = sessionState.asStateFlow()

    override suspend fun hasValidSession(): Boolean {
        if (sessionState.value != null) {
            return true
        }
        val loaded = loadSession(configRepository.getCurrentBackendUrl())
        if (loaded != null) {
            sessionState.value = loaded
        }
        return loaded != null
    }

    override fun saveSession(session: UserSession) {
        sessionState.value = session
        val backendUrl = configRepository.getCurrentBackendUrl()
        scope.launch {
            runCatching {
                secureStorage.putString(scopedKey(StorageKeys.ACCESS_TOKEN, backendUrl), session.accessToken)
                secureStorage.putString(scopedKey(StorageKeys.REFRESH_TOKEN, backendUrl), session.refreshToken)
                secureStorage.putString(scopedKey(StorageKeys.ACCOUNT_ID, backendUrl), session.accountId)
            }.onFailure { error ->
                Napier.e("Failed to persist secure session", error)
            }
        }
    }

    override fun clearSession() {
        sessionState.value = null
        val backendUrl = configRepository.getCurrentBackendUrl()
        scope.launch {
            runCatching {
                secureStorage.remove(scopedKey(StorageKeys.ACCESS_TOKEN, backendUrl))
                secureStorage.remove(scopedKey(StorageKeys.REFRESH_TOKEN, backendUrl))
                secureStorage.remove(scopedKey(StorageKeys.ACCOUNT_ID, backendUrl))
            }.onFailure { error ->
                Napier.e("Failed to clear secure session", error)
            }
        }
    }

    private suspend fun loadSession(backendUrl: String): UserSession? =
        runCatching {
            migrateLegacyKeysIfNeeded(backendUrl)

            val accessToken = secureStorage.getString(scopedKey(StorageKeys.ACCESS_TOKEN, backendUrl))
            val refreshToken = secureStorage.getString(scopedKey(StorageKeys.REFRESH_TOKEN, backendUrl))
            val accountId = secureStorage.getString(scopedKey(StorageKeys.ACCOUNT_ID, backendUrl))

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

    private suspend fun migrateLegacyKeysIfNeeded(backendUrl: String) {
        if (secureStorage.getString(scopedKey(StorageKeys.ACCESS_TOKEN, backendUrl)) != null) {
            return
        }

        val legacyAccessToken = secureStorage.getString(StorageKeys.ACCESS_TOKEN) ?: return
        val legacyRefreshToken = secureStorage.getString(StorageKeys.REFRESH_TOKEN) ?: return
        val legacyAccountId = secureStorage.getString(StorageKeys.ACCOUNT_ID) ?: return

        secureStorage.putString(scopedKey(StorageKeys.ACCESS_TOKEN, backendUrl), legacyAccessToken)
        secureStorage.putString(scopedKey(StorageKeys.REFRESH_TOKEN, backendUrl), legacyRefreshToken)
        secureStorage.putString(scopedKey(StorageKeys.ACCOUNT_ID, backendUrl), legacyAccountId)
        secureStorage.remove(StorageKeys.ACCESS_TOKEN)
        secureStorage.remove(StorageKeys.REFRESH_TOKEN)
        secureStorage.remove(StorageKeys.ACCOUNT_ID)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun scopedKey(
        baseKey: String,
        backendUrl: String,
    ): String = "${baseKey}_${Base64.UrlSafe.encode(backendUrl.trim().encodeToByteArray()).trimEnd('=')}"
}
