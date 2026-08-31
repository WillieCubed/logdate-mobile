package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.logdate.shared.config.DefaultLogDateConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SessionStorageBackendChangeTest {
    @Test
    fun changingBackendDoesNotReviveThePreviousSession() =
        runTest {
            val dataStore = SessionTestPreferencesDataStore()
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://first.logdate.app")
            val storage =
                DataStoreSessionStorage(
                    dataStore = dataStore,
                    configRepository = config,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                )
            val session = UserSession("access", "refresh", "account")

            storage.saveSession(session)
            advanceUntilIdle()
            config.updateBackendUrl("https://second.logdate.app")
            advanceUntilIdle()
            config.updateBackendUrl("https://first.logdate.app")
            advanceUntilIdle()

            assertNull(storage.getSession())
        }
}

private class SessionTestPreferencesDataStore : DataStore<Preferences> {
    private val preferences = MutableStateFlow<Preferences>(emptyPreferences())

    override val data: Flow<Preferences> = preferences

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(preferences.value)
        preferences.value = updated
        return updated
    }
}
