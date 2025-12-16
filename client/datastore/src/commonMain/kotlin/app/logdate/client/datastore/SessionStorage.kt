package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Data class representing a user session
 */
data class UserSession(
    val accessToken: String,
    val refreshToken: String,
    val accountId: String
)

/**
 * Interface for session storage
 */
interface SessionStorage {
    /**
     * Gets the current session synchronously.
     * May block on first call.
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
    private val dataStore: DataStore<Preferences>
) : SessionStorage {
    
    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val ACCOUNT_ID_KEY = stringPreferencesKey("account_id")
    }
    
    override fun getSession(): UserSession? {
        return try {
            val preferences = kotlinx.coroutines.runBlocking {
                dataStore.data.first()
            }
            
            val accessToken = preferences[ACCESS_TOKEN_KEY]
            val refreshToken = preferences[REFRESH_TOKEN_KEY]
            val accountId = preferences[ACCOUNT_ID_KEY]
            
            if (accessToken != null && refreshToken != null && accountId != null) {
                UserSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    accountId = accountId
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override fun saveSession(session: UserSession) {
        kotlinx.coroutines.runBlocking {
            dataStore.edit { preferences ->
                preferences[ACCESS_TOKEN_KEY] = session.accessToken
                preferences[REFRESH_TOKEN_KEY] = session.refreshToken
                preferences[ACCOUNT_ID_KEY] = session.accountId
            }
        }
    }
    
    override fun clearSession() {
        kotlinx.coroutines.runBlocking {
            dataStore.edit { preferences ->
                preferences.remove(ACCESS_TOKEN_KEY)
                preferences.remove(REFRESH_TOKEN_KEY)
                preferences.remove(ACCOUNT_ID_KEY)
            }
        }
    }
    
    /**
     * Flow-based version of getSession for reactive use cases
     */
    override fun getSessionFlow() = dataStore.data.map { preferences ->
        val accessToken = preferences[ACCESS_TOKEN_KEY]
        val refreshToken = preferences[REFRESH_TOKEN_KEY]
        val accountId = preferences[ACCOUNT_ID_KEY]
        
        if (accessToken != null && refreshToken != null && accountId != null) {
            UserSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                accountId = accountId
            )
        } else {
            null
        }
    }
    
    /**
     * Check if a valid session exists
     */
    override suspend fun hasValidSession(): Boolean {
        return try {
            val preferences = dataStore.data.first()
            preferences[ACCESS_TOKEN_KEY] != null && 
            preferences[REFRESH_TOKEN_KEY] != null && 
            preferences[ACCOUNT_ID_KEY] != null
        } catch (e: Exception) {
            false
        }
    }
}