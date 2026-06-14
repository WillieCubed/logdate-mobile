package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.shared.config.LogDateConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.text.encodeToByteArray

/**
 * Data class representing a user session
 */
data class UserSession(
    val accessToken: String,
    val refreshToken: String,
    val accountId: String,
)

/**
 * Interface for session storage
 */
interface SessionStorage {
    /**
     * Gets the current session synchronously from the latest cached value.
     */
    fun getSession(): UserSession?

    /**
     * Observes session changes as a Flow.
     * Emits null when no session exists, UserSession when authenticated.
     */
    fun getSessionFlow(): kotlinx.coroutines.flow.Flow<UserSession?>

    /**
     * Checks if a valid session exists.
     */
    suspend fun hasValidSession(): Boolean

    /**
     * Saves a new session.
     */
    fun saveSession(session: UserSession)

    /**
     * Clears the current session.
     */
    fun clearSession()
}

/**
 * DataStore-based implementation of SessionStorage for secure token persistence
 */
class DataStoreSessionStorage(
    private val dataStore: DataStore<Preferences>,
    private val configRepository: LogDateConfigRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : SessionStorage {
    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val ACCOUNT_ID_KEY = stringPreferencesKey("account_id")
    }

    private val sessionState = MutableStateFlow<UserSession?>(null)

    init {
        scope.launch {
            combine(
                dataStore.data,
                configRepository.backendUrl,
            ) { preferences, backendUrl ->
                decodeSession(preferences, backendUrl)
            }.distinctUntilChanged()
                .collect { sessionState.value = it }
        }
    }

    override fun getSession(): UserSession? = sessionState.value

    override fun saveSession(session: UserSession) {
        sessionState.value = session
        val backendUrl = configRepository.getCurrentBackendUrl()
        scope.launch {
            dataStore.edit { preferences ->
                preferences[scopedKey(ACCESS_TOKEN_KEY.name, backendUrl)] = session.accessToken
                preferences[scopedKey(REFRESH_TOKEN_KEY.name, backendUrl)] = session.refreshToken
                preferences[scopedKey(ACCOUNT_ID_KEY.name, backendUrl)] = session.accountId
            }
        }
    }

    override fun clearSession() {
        sessionState.value = null
        val backendUrl = configRepository.getCurrentBackendUrl()
        scope.launch {
            dataStore.edit { preferences ->
                preferences.remove(scopedKey(ACCESS_TOKEN_KEY.name, backendUrl))
                preferences.remove(scopedKey(REFRESH_TOKEN_KEY.name, backendUrl))
                preferences.remove(scopedKey(ACCOUNT_ID_KEY.name, backendUrl))
            }
        }
    }

    /**
     * Flow-based version of getSession for reactive use cases
     */
    override fun getSessionFlow() = sessionState.asStateFlow()

    /**
     * Check if a valid session exists
     */
    override suspend fun hasValidSession(): Boolean {
        val cached = sessionState.value
        if (cached != null) {
            return true
        }
        return try {
            val preferences = dataStore.data.first()
            val backendUrl = configRepository.getCurrentBackendUrl()
            val loaded = decodeSession(preferences, backendUrl)
            if (loaded != null) {
                sessionState.value = loaded
            }
            loaded != null
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun decodeSession(
        preferences: Preferences,
        backendUrl: String,
    ): UserSession? {
        migrateLegacyKeysIfNeeded(preferences, backendUrl)

        val accessToken = preferences[scopedKey(ACCESS_TOKEN_KEY.name, backendUrl)]
        val refreshToken = preferences[scopedKey(REFRESH_TOKEN_KEY.name, backendUrl)]
        val accountId = preferences[scopedKey(ACCOUNT_ID_KEY.name, backendUrl)]

        return if (accessToken != null && refreshToken != null && accountId != null) {
            UserSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                accountId = accountId,
            )
        } else {
            null
        }
    }

    private suspend fun migrateLegacyKeysIfNeeded(
        preferences: Preferences,
        backendUrl: String,
    ) {
        val scopedAccessKey = scopedKey(ACCESS_TOKEN_KEY.name, backendUrl)
        if (preferences[scopedAccessKey] != null) {
            return
        }

        val legacyAccessToken = preferences[ACCESS_TOKEN_KEY] ?: return
        val legacyRefreshToken = preferences[REFRESH_TOKEN_KEY] ?: return
        val legacyAccountId = preferences[ACCOUNT_ID_KEY] ?: return

        dataStore.edit { mutablePreferences ->
            mutablePreferences[scopedAccessKey] = legacyAccessToken
            mutablePreferences[scopedKey(REFRESH_TOKEN_KEY.name, backendUrl)] = legacyRefreshToken
            mutablePreferences[scopedKey(ACCOUNT_ID_KEY.name, backendUrl)] = legacyAccountId
            mutablePreferences.remove(ACCESS_TOKEN_KEY)
            mutablePreferences.remove(REFRESH_TOKEN_KEY)
            mutablePreferences.remove(ACCOUNT_ID_KEY)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun scopedKey(
        baseKey: String,
        backendUrl: String,
    ) = stringPreferencesKey("${baseKey}_${Base64.UrlSafe.encode(backendUrl.trim().encodeToByteArray()).trimEnd('=')}")
}
