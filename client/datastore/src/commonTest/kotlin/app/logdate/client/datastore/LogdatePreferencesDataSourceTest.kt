package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogdatePreferencesDataSourceTest {
    private val dataSource = LogdatePreferencesDataSource(TestPreferencesDataStore())

    @Test
    fun systemSearchVisibility_defaultsToEnabled() =
        runTest {
            assertTrue(dataSource.getSystemSearchVisibilityEnabled())
        }

    @Test
    fun peopleFeature_defaultsToEnabled() =
        runTest {
            assertEquals(true, dataSource.observePeopleEnabled().first())
        }

    @Test
    fun systemSearchVisibility_canBePersisted() =
        runTest {
            dataSource.setSystemSearchVisibilityEnabled(true)

            assertEquals(true, dataSource.getSystemSearchVisibilityEnabled())
        }

    @Test
    fun androidPlatformSearchIndexState_canBePersisted() =
        runTest {
            dataSource.setAndroidPlatformSearchIndexState(
                generation = 42L,
                schemaVersion = 3,
            )

            assertEquals(42L, dataSource.getAndroidPlatformSearchIndexedGeneration())
            assertEquals(3, dataSource.getAndroidPlatformSearchSchemaVersion())
        }
}

private class TestPreferencesDataStore : DataStore<Preferences> {
    private val preferencesFlow = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = preferencesFlow

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updatedPreferences = transform(preferencesFlow.value)
        preferencesFlow.value = updatedPreferences
        return updatedPreferences
    }
}
