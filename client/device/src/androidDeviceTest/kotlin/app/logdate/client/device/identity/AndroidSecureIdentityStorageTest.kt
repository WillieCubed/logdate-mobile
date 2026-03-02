package app.logdate.client.device.identity

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class AndroidSecureIdentityStorageTest {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var context: Context
    private lateinit var storage: AndroidSecureIdentityStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataStore = InMemoryPreferencesDataStore()
        storage = AndroidSecureIdentityStorage(dataStore)
    }

    @Test
    fun `getUserId should return null when no ID is set`() =
        runTest {
            // When
            val userId = storage.getUserId()

            // Then
            assertNull(userId, "User ID should be null when not set")
        }

    @Test
    fun `setUserId should store the ID`() =
        runTest {
            // Given
            val testId = Uuid.random()

            // When
            storage.setUserId(testId)

            // Then
            assertEquals(testId, storage.getUserId())
        }

    @Test
    fun `getMigrationState should return null when no state is set`() =
        runTest {
            // When
            val state = storage.getMigrationState()

            // Then
            assertNull(state, "Migration state should be null when not set")
        }

    @Test
    fun `setMigrationState should store the state`() =
        runTest {
            // Given
            val testState =
                MigrationState(
                    fromUserId = Uuid.random(),
                    toUserId = Uuid.random(),
                    progress = 0.5f,
                    timestamp = 123456789L,
                )

            // When
            storage.setMigrationState(testState)

            // Then
            assertEquals(testState, storage.getMigrationState())
        }

    @Test
    fun `clearMigrationState should remove the state`() =
        runTest {
            // Given
            val testState =
                MigrationState(
                    fromUserId = Uuid.random(),
                    toUserId = Uuid.random(),
                    progress = 0.5f,
                    timestamp = 123456789L,
                )
            storage.setMigrationState(testState)

            // When
            storage.clearMigrationState()

            // Then
            assertNull(storage.getMigrationState())
        }

    @Test
    fun `clear should remove both user ID and migration state`() =
        runTest {
            // Given
            val testId = Uuid.random()
            val testState =
                MigrationState(
                    fromUserId = Uuid.random(),
                    toUserId = Uuid.random(),
                    progress = 0.5f,
                    timestamp = 123456789L,
                )
            storage.setUserId(testId)
            storage.setMigrationState(testState)

            // When
            storage.clear()

            // Then
            assertNull(storage.getUserId())
            assertNull(storage.getMigrationState())
        }

    private class InMemoryPreferencesDataStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
